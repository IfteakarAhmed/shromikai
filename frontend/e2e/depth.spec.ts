import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { inLocale, openResult, text } from './support'

/**
 * The CSS 3D depth system (C2b): cards float, lean towards a fine pointer and lift their badges,
 * citations and actions in front of the glass; orbs and headers drift on scroll. On touch, with
 * reduced motion and in low-bandwidth mode cards stay flat. Controls inside a leaning card stay
 * clickable and the page stays accessible.
 *
 * Pictures: P9_SHOTS=C2b npx playwright test e2e/depth.spec.ts
 */
const shots = process.env['P9_SHOTS'] === 'C2b'
const folder = resolve(process.cwd(), '..', 'docs', 'img', 'p9')

async function inTheme(page: Page, theme: 'dark' | 'light') {
  await page.addInitScript((value) => {
    window.localStorage.setItem('shromikai.theme', value)
  }, theme)
}

async function shot(page: Page, name: string, theme: string) {
  if (!shots) {
    return
  }
  mkdirSync(folder, { recursive: true })
  const width = page.viewportSize()?.width ?? 0
  await page.screenshot({
    path: resolve(folder, `C2b-${name}-${theme}-${String(width)}.png`),
  })
}

async function transformOf(page: Page, selector: string): Promise<string> {
  return page
    .locator(selector)
    .first()
    .evaluate((element) => getComputedStyle(element).transform)
}

/** The centre of the first risk card, nudged towards its right edge. */
async function hoverFirstRiskCard(page: Page): Promise<string> {
  const card = page.locator('article.depth-card').first()
  await card.scrollIntoViewIfNeeded()
  const box = await card.boundingBox()
  if (box === null) {
    throw new Error('no risk card')
  }
  await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.2)
  await page.mouse.move(box.x + box.width * 0.85, box.y + box.height * 0.3, { steps: 6 })
  return 'article.depth-card'
}

test.describe('depth system', () => {
  test.beforeEach(async ({ page }) => {
    await inLocale(page, 'bn')
  })

  test('FR-68 a risk card leans towards a fine pointer, lifts its parts and settles flat again', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await openResult(page, 'bn')
    const selector = await hoverFirstRiskCard(page)
    await expect.poll(() => transformOf(page, selector), { timeout: 5_000 }).toMatch(/^matrix3d/)
    await expect(page.locator(selector).first()).toHaveAttribute('data-tilting', 'true')
    // The badge row, the citations and the actions float in front of the glass.
    const lifts = await page
      .locator(`${selector} .lift-2, ${selector} .lift-1`)
      .first()
      .evaluate((element) => getComputedStyle(element).translate)
    expect(lifts).toMatch(/px$/)
    // The glass layer is behind everything and does not take the pointer.
    const glass = await page
      .locator(`${selector} > .depth-glass`)
      .first()
      .evaluate((element) => {
        const style = getComputedStyle(element)
        return { events: style.pointerEvents, blur: style.backdropFilter }
      })
    expect(glass.events).toBe('none')
    expect(glass.blur).toMatch(/blur/)
    await page.mouse.move(2, 2)
    await expect.poll(() => transformOf(page, selector), { timeout: 5_000 }).toBe('none')
  })

  test('FR-70 the why button inside a leaning card still opens its sheet', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await openResult(page, 'bn')
    const selector = await hoverFirstRiskCard(page)
    await expect.poll(() => transformOf(page, selector)).toMatch(/^matrix3d/)
    await page
      .locator(selector)
      .first()
      .getByRole('button', { name: text('bn', 'finding.why') })
      .click()
    await expect(page.getByRole('dialog')).toBeVisible()
  })

  test('NFR-08 cards stay flat when the visitor prefers reduced motion', async ({ page }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await openResult(page, 'bn')
    const selector = await hoverFirstRiskCard(page)
    await page.waitForTimeout(400)
    expect(await transformOf(page, selector)).toBe('none')
    await expect(page.locator(selector).first()).not.toHaveAttribute('data-tilt', 'on')
    const lift = await page
      .locator(`${selector} .lift-2`)
      .first()
      .evaluate((element) => getComputedStyle(element).translate)
    expect(lift).toBe('none')
    // The glass is still there: the same look, flat.
    await expect(page.locator(`${selector} > .depth-glass`).first()).toBeAttached()
  })

  test('FR-76 cards stay flat in low-bandwidth mode', async ({ page }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await page.addInitScript(() => {
      window.localStorage.setItem('shromikai.lowbw', 'true')
    })
    await openResult(page, 'bn')
    const selector = await hoverFirstRiskCard(page)
    await page.waitForTimeout(400)
    expect(await transformOf(page, selector)).toBe('none')
    await expect(page.locator(selector).first()).not.toHaveAttribute('data-tilt', 'on')
  })

  test('FR-76 cards stay flat on a touch phone and nothing drifts on scroll', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'mobile-chromium', 'touch only')
    await openResult(page, 'bn')
    const card = page.locator('article.depth-card').first()
    await expect(card).not.toHaveAttribute('data-tilt', 'on')
    expect(await transformOf(page, 'article.depth-card')).toBe('none')
    const drift = await page
      .locator('[data-parallax="1"]')
      .first()
      .evaluate((element) => getComputedStyle(element).animationName)
    expect(drift).toBe('none')
    const header = await page
      .locator('.parallax')
      .first()
      .evaluate((element) => getComputedStyle(element).animationName)
    expect(header).toBe('none')
  })

  test('FR-68 the orbs and page headers drift on scroll with a fine pointer', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await openResult(page, 'bn')
    const names = await page.evaluate(() => ({
      orb: getComputedStyle(document.querySelector('[data-parallax="1"]') as Element).animationName,
      header: getComputedStyle(document.querySelector('.parallax') as Element).animationName,
    }))
    expect(names.orb).toBe('orb-drift-up')
    expect(names.header).toBe('parallax-drift')
    // Reduced motion removes it.
    await page.emulateMedia({ reducedMotion: 'reduce' })
    const reduced = await page.evaluate(() => ({
      orb: getComputedStyle(document.querySelector('[data-parallax="1"]') as Element).animationName,
      header: getComputedStyle(document.querySelector('.parallax') as Element).animationName,
    }))
    expect(reduced).toEqual({ orb: 'none', header: 'none' })
  })

  test('FR-71 the printed memo is a plain document: no glass, no glow, no tilt', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'one width is enough')
    await openResult(page, 'bn')
    await page.goto(`${new URL(page.url()).pathname}?tab=memo`)
    const memo = page.locator('.memo-print')
    await expect(memo).toBeVisible()
    await page.emulateMedia({ media: 'print' })
    const printed = await memo.evaluate((element) => ({
      glass: getComputedStyle(element.querySelector('.depth-glass') as Element).display,
      glow: getComputedStyle(element.querySelector('.depth-glow') as Element).display,
      transform: getComputedStyle(element).transform,
    }))
    expect(printed).toEqual({ glass: 'none', glow: 'none', transform: 'none' })
  })

  for (const theme of ['dark', 'light'] as const) {
    test(`NFR-08 axe, with a card leaning, on the result and the memo (${theme})`, async ({
      page,
    }, info) => {
      test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
      await inTheme(page, theme)
      await openResult(page, 'bn')
      await hoverFirstRiskCard(page)
      await page.waitForTimeout(400)
      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze()
      const serious = results.violations.filter(
        (violation) => violation.impact === 'serious' || violation.impact === 'critical',
      )
      expect(
        serious.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`),
        `axe on the leaning result (${theme})`,
      ).toEqual([])
    })
  }

  for (const theme of ['dark', 'light'] as const) {
    test(`report pictures of the depth system (${theme})`, async ({ page }) => {
      test.skip(!shots, 'set P9_SHOTS=C2b to take the report pictures')
      test.setTimeout(300_000)
      await inTheme(page, theme)
      await page.goto('/login')
      await page.waitForLoadState('networkidle')
      await shot(page, 'login', theme)
      await openResult(page, 'bn')
      await page.waitForTimeout(600)
      await shot(page, 'results-top', theme)
      await hoverFirstRiskCard(page).catch(() => undefined)
      await page.waitForTimeout(500)
      await shot(page, 'results-card-hover', theme)
      await page.mouse.move(2, 2)
      await page.goto(`${new URL(page.url()).pathname}?tab=memo`)
      await expect(page.locator('.memo-print')).toBeVisible()
      await page.locator('.memo-print').scrollIntoViewIfNeeded()
      await page.waitForTimeout(500)
      await shot(page, 'memo', theme)
      await page.goto('/app/compare')
      await page.getByRole('button', { name: text('bn', 'compare.start') }).click()
      await expect(page.getByTestId('compare-summary')).toBeVisible({ timeout: 90_000 })
      await page.waitForTimeout(800)
      await shot(page, 'compare', theme)
      await page.goto('/?scene=lite')
      await page.waitForLoadState('networkidle')
      await page.locator('#audience-title').scrollIntoViewIfNeeded()
      await page.waitForTimeout(600)
      await shot(page, 'landing-cards', theme)
    })
  }
})
