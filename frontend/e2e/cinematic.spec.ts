import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { inLocale, LOCALES, text } from './support'

/**
 * The cinematic landing (FR-68, FR-76, NFR-08). Headless Chromium has no GPU, so these tests launch
 * it with SwiftShader (software WebGL2). That draws the real shaders, slowly; the adaptive quality
 * then lowers particles and bloom, which is what a slow machine would get too.
 *
 * Pictures for the report are taken only on request:
 *   P9_SHOTS=D npx playwright test e2e/cinematic.spec.ts --project=desktop-chromium
 */
test.use({
  launchOptions: {
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
  },
})

const shots = process.env['P9_SHOTS'] === 'D'
const folder = resolve(process.cwd(), '..', 'docs', 'img', 'p9')

async function inTheme(page: Page, theme: 'dark' | 'light') {
  await page.addInitScript((value) => {
    window.localStorage.setItem('shromikai.theme', value)
  }, theme)
}

async function expectNoSeriousViolations(page: Page, where: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const serious = results.violations.filter(
    (violation) => violation.impact === 'serious' || violation.impact === 'critical',
  )
  expect(
    serious.map(
      (v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`,
    ),
    `axe on ${where}`,
  ).toEqual([])
}

/**
 * Scrolls so that the middle of the viewport sits at timeline value `t` among the chapters, and
 * returns the value the page actually reached (at the very top the hero has the header above it,
 * so the smallest reachable value is a little above 0).
 */
async function scrollToTimeline(page: Page, t: number): Promise<number> {
  return page.evaluate((value) => {
    const measure = () => {
      const sections = Array.from(document.querySelectorAll<HTMLElement>('[data-chapter]'))
      const starts = sections.map((s) => s.getBoundingClientRect().top + window.scrollY)
      const last = sections.at(-1)
      const end = last === undefined ? 0 : last.getBoundingClientRect().bottom + window.scrollY
      return { starts, end, count: sections.length }
    }
    const { starts, end, count } = measure()
    const scaled = Math.min(1, Math.max(0, value)) * count
    const index = Math.min(count - 1, Math.floor(scaled))
    const start = starts[index] ?? 0
    const next = starts[index + 1] ?? end
    const probe = value >= 1 ? end - 1 : start + (scaled - index) * (next - start)
    window.scrollTo({ top: probe - window.innerHeight / 2, behavior: 'instant' })
    // The same mapping as timelineFromScroll in the app.
    const at = window.scrollY + window.innerHeight / 2
    const first = starts[0] ?? 0
    if (at <= first) return 0
    if (at >= end) return 1
    let i = 0
    starts.forEach((s, k) => {
      if (s <= at) i = k
    })
    const from = starts[i] ?? first
    const to = starts[i + 1] ?? end
    return Math.min(1, (i + (at - from) / (to - from)) / count)
  }, t)
}

/** Waits until the damped camera has arrived near `t` (software rendering is slow). */
async function waitForCamera(page: Page, t: number) {
  const progress = async () =>
    Number((await page.locator('[data-scene="cinematic"]').getAttribute('data-progress')) ?? -1)
  await expect
    .poll(async () => Math.abs((await progress()) - t), { timeout: 90_000, intervals: [250] })
    .toBeLessThan(0.004)
}

test.describe('cinematic landing', () => {
  test.beforeEach(async ({ page }) => {
    await inLocale(page, 'bn')
  })

  test('FR-76 a phone gets the poster and fetches no scene code; the scene can be turned on', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'mobile-chromium', 'phone width only')
    const sceneRequests: string[] = []
    page.on('request', (request) => {
      if (
        /CinematicScene|cinematic\/scene|ContractScene|scene3d|node_modules\/.*three/.test(
          request.url(),
        )
      ) {
        sceneRequests.push(request.url())
      }
    })
    await page.goto('/')
    await page.waitForLoadState('networkidle')

    await expect(page.locator('[data-tier]')).toHaveAttribute('data-tier', 'lite')
    await expect(page.getByRole('img', { name: text('bn', 'hero.sceneAlt') })).toBeVisible()
    expect(sceneRequests).toEqual([])

    await page.getByRole('button', { name: text('bn', 'scene.enable') }).click()
    await expect(page.locator('[data-tier]')).toHaveAttribute('data-tier', 'standard')
    await expect(page.locator('[data-hero="scene"] canvas')).toBeAttached({ timeout: 20_000 })
    await expect(page.getByRole('button', { name: text('bn', 'scene.still') })).toBeVisible()
  })

  test('FR-68 the cinematic tier draws one fixed WebGL2 canvas and switches to the poster on request', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'desktop width only')
    await page.goto('/?scene=cinematic')
    await expect(page.locator('[data-tier]')).toHaveAttribute('data-tier', 'cinematic')
    const canvas = page.locator('[data-scene="cinematic"] canvas')
    await expect(canvas).toBeAttached({ timeout: 30_000 })
    expect(
      await canvas.evaluate(
        (element) => (element as HTMLCanvasElement).getContext('webgl2') !== null,
      ),
    ).toBe(true)
    await expect(canvas).toHaveCSS('position', 'absolute')
    await expect(page.locator('[data-scene="cinematic"]')).toHaveCSS('position', 'fixed')

    await page.getByRole('button', { name: text('bn', 'scene.still') }).click()
    await expect(page.locator('[data-scene="cinematic"]')).toHaveCount(0)
    await expect(page.getByRole('img', { name: text('bn', 'hero.sceneAlt') })).toBeVisible()
  })

  test('FR-68 native scrolling moves the camera through the chapters (no scroll hijacking)', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'desktop width only')
    await page.goto('/?scene=cinematic')
    await expect(page.locator('[data-scene="cinematic"] canvas')).toBeAttached({ timeout: 30_000 })
    const before = await page.evaluate(() => window.scrollY)
    await page.mouse.wheel(0, 1200)
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(before + 600)
    await waitForCamera(page, await scrollToTimeline(page, 0.5))
  })

  test('NFR-08 keyboard only: the skip link comes first, then every control is reachable and in view', async ({
    page,
  }) => {
    await page.goto('/?scene=cinematic')
    await page.waitForLoadState('networkidle')
    await page.keyboard.press('Tab')
    await expect(page.locator(':focus')).toHaveText(text('bn', 'nav.skip'))
    await page.keyboard.press('Enter')
    await expect(page.locator('#main')).toBeFocused()

    const seen: string[] = []
    for (let i = 0; i < 60; i += 1) {
      await page.keyboard.press('Tab')
      const focused = page.locator(':focus')
      if ((await focused.count()) === 0) {
        continue
      }
      const name = (
        (await focused.getAttribute('aria-label')) ?? (await focused.innerText())
      ).trim()
      seen.push(name)
      await expect(focused, `focused "${name}" is visible`).toBeInViewport()
    }
    const joined = seen.join(' | ')
    expect(joined).toContain(text('bn', 'hero.sample'))
    expect(joined).toContain(text('bn', 'comparison.cta'))
    expect(joined).toContain(text('bn', 'story.finaleSample'))
    expect(joined).toMatch(new RegExp(`${text('bn', 'scene.still')}|${text('bn', 'scene.enable')}`))
  })

  for (const locale of LOCALES) {
    for (const theme of ['dark', 'light'] as const) {
      for (const scene of ['lite', 'standard', 'cinematic'] as const) {
        test(`NFR-08 axe on the ${scene} landing (${locale}, ${theme})`, async ({ page }) => {
          await inLocale(page, locale)
          await inTheme(page, theme)
          await page.goto(`/?scene=${scene}`)
          await page.waitForLoadState('networkidle')
          await expectNoSeriousViolations(page, `/?scene=${scene}`)
          await scrollToTimeline(page, 0.75)
          await expectNoSeriousViolations(page, `/?scene=${scene} at the invented-law chapter`)
        })
      }
    }
  }

  test('report frames of the cinematic scene at 0, 25, 50, 75 and 100 % of the timeline', async ({
    page,
  }, info) => {
    test.skip(!shots, 'set P9_SHOTS=D to take the report pictures')
    test.skip(info.project.name !== 'desktop-chromium', 'desktop width only')
    test.setTimeout(600_000)
    mkdirSync(folder, { recursive: true })
    await page.goto('/?scene=cinematic')
    await expect(page.locator('[data-scene="cinematic"] canvas')).toBeAttached({ timeout: 30_000 })
    for (const percent of [0, 25, 50, 75, 100]) {
      await waitForCamera(page, await scrollToTimeline(page, percent / 100))
      await page.waitForTimeout(1500)
      await page.screenshot({ path: resolve(folder, `D-landing-cinematic-${percent}-1280.png`) })
    }
    const quality = await page.locator('[data-scene="cinematic"]').getAttribute('data-quality')
    test.info().annotations.push({ type: 'quality level at the end', description: quality ?? '?' })
  })

  test('report pictures of the lite and standard landing', async ({ page }, info) => {
    test.skip(!shots, 'set P9_SHOTS=D to take the report pictures')
    mkdirSync(folder, { recursive: true })
    const width = info.project.name === 'mobile-chromium' ? 390 : 1280
    await page.goto('/?scene=lite')
    await page.waitForLoadState('networkidle')
    await page.screenshot({ path: resolve(folder, `D-landing-lite-${width}.png`) })
    await page.goto('/?scene=standard')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('[data-hero="scene"] canvas')).toBeAttached({ timeout: 30_000 })
    await page.locator('[data-hero]').scrollIntoViewIfNeeded()
    await page.waitForTimeout(2500)
    await page.screenshot({ path: resolve(folder, `D-landing-standard-${width}.png`) })
  })
})
