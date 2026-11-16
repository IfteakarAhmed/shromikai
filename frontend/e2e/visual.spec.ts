import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { analyseSample, inLocale, text } from './support'

/**
 * The C1 visual language: self-hosted type pairing, readable Bangla conjuncts, 18 px body text,
 * 56 px touch targets on phones, ambient glow only where motion and bandwidth allow it.
 *
 * Pictures for the report: P9_SHOTS=C1 npx playwright test e2e/visual.spec.ts
 */
const shots = process.env['P9_SHOTS'] === 'C1'
const folder = resolve(process.cwd(), '..', 'docs', 'img', 'p9')
const CONJUNCTS = ['শ্র', 'ক্ষ', 'স্ক', 'ত্র', 'শ্ব', 'ঞ্চ', 'ন্ত']

async function shot(page: Page, name: string, fullPage = false) {
  if (!shots) {
    return
  }
  mkdirSync(folder, { recursive: true })
  const width = page.viewportSize()?.width ?? 0
  await page.screenshot({ path: resolve(folder, `C1-${name}-${String(width)}.png`), fullPage })
}

async function inTheme(page: Page, theme: 'dark' | 'light') {
  await page.addInitScript((value) => {
    window.localStorage.setItem('shromikai.theme', value)
  }, theme)
}

test.describe('visual language (C1)', () => {
  test.beforeEach(async ({ page }) => {
    await inLocale(page, 'bn')
  })

  test('NFR-09 headings, body and labels use the three self-hosted faces, with no font CDN', async ({
    page,
  }) => {
    const external: string[] = []
    page.on('request', (request) => {
      if (/fonts\.googleapis|fonts\.gstatic|typekit|bunny\.net/.test(request.url())) {
        external.push(request.url())
      }
    })
    await page.goto('/?scene=lite')
    await page.evaluate(() => document.fonts.ready)
    const families = await page.evaluate(() => ({
      h1: getComputedStyle(document.querySelector('h1') as Element).fontFamily,
      body: getComputedStyle(document.body).fontFamily,
      tiro: document.fonts.check('40px "Tiro Bangla"', 'শ্র'),
      hind: document.fonts.check('18px "Hind Siliguri"', 'শ্র'),
    }))
    expect(families.h1).toMatch(/^"?Tiro Bangla/)
    expect(families.body).toMatch(/^"?Hind Siliguri/)
    expect(families.tiro).toBe(true)
    expect(families.hind).toBe(true)
    await page.goto('/docs')
    await page.waitForLoadState('networkidle')
    expect(external).toEqual([])
  })

  test('NFR-09 Bangla conjuncts fit their line boxes at 18 px body and heading sizes', async ({
    page,
  }) => {
    await page.goto('/login')
    await page.evaluate(() => document.fonts.ready)
    // A specimen in the app's own styles: a page heading, a small section heading, body text.
    await page.evaluate((conjuncts) => {
      const main = document.querySelector('main')
      const box = document.createElement('section')
      box.id = 'conjunct-specimen'
      box.className = 'mx-auto max-w-6xl px-(--gutter) py-6'
      const sentence = 'শ্রমিকের স্বাক্ষর, ক্ষতিপূরণ, স্কুল, পত্র, শ্বশুর, পঞ্চাশ, অন্তত'
      box.innerHTML = `<h1 class="text-4xl font-bold leading-tight">${conjuncts.join(' ')}</h1>
        <h2 class="mt-3 text-2xl font-bold">${conjuncts.join(' ')} · ${sentence}</h2>
        <p class="mt-3">${conjuncts.join(' ')} · ${sentence}</p>`
      main?.prepend(box)
    }, CONJUNCTS)
    const metrics = await page.evaluate((conjuncts) => {
      const canvas = document.createElement('canvas').getContext('2d')
      if (canvas === null) {
        throw new Error('no 2D canvas')
      }
      return ['h1', 'h2', 'p'].map((tag) => {
        const element = document.querySelector(`#conjunct-specimen ${tag}`) as HTMLElement
        const style = getComputedStyle(element)
        canvas.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`
        const worst = Math.max(
          ...conjuncts.map((c) => {
            const m = canvas.measureText(c)
            return m.actualBoundingBoxAscent + m.actualBoundingBoxDescent
          }),
        )
        return {
          tag,
          fontSize: parseFloat(style.fontSize),
          lineHeight: parseFloat(style.lineHeight),
          ink: worst,
        }
      })
    }, CONJUNCTS)
    for (const { tag, fontSize, lineHeight, ink } of metrics) {
      expect(
        ink,
        `${tag}: conjunct ink ${ink.toFixed(1)} px within line ${String(lineHeight)} px`,
      ).toBeLessThanOrEqual(lineHeight)
      if (tag === 'p') {
        expect(fontSize).toBe(18)
      }
    }
    await page.locator('#conjunct-specimen').scrollIntoViewIfNeeded()
    await shot(page, 'conjuncts')
  })

  test('NFR-09 body text is 18 px on the app pages', async ({ page }) => {
    await page.goto('/login')
    expect(await page.evaluate(() => getComputedStyle(document.body).fontSize)).toBe('18px')
  })

  test('NFR-08 every control is at least 56 px tall on a phone', async ({ page }, info) => {
    test.skip(info.project.name !== 'mobile-chromium', 'phone width only')
    async function check(where: string) {
      await page.waitForLoadState('networkidle')
      await page.evaluate(() => document.fonts.ready)
      const small = await page.evaluate(() =>
        Array.from(
          document.querySelectorAll<HTMLElement>(
            'header a, header button, main .btn, main [role="tab"], main button',
          ),
        )
          .filter((element) => element.offsetParent !== null)
          .filter((element) => !element.closest('p, li > span, .inline-link'))
          .map((element) => ({
            name: (element.innerText || element.getAttribute('aria-label') || '').trim(),
            height: element.getBoundingClientRect().height,
          }))
          .filter(({ height }) => height < 55.5),
      )
      expect(small, `controls under 56 px on ${where}`).toEqual([])
    }
    await page.goto('/?scene=lite')
    await check('/')
    await page.goto('/login')
    await check('/login')
    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text('bn', 'upload.title') })).toBeVisible()
    await check('/app')
  })

  test('NFR-08 the glow orbs float, stay still with reduced motion and vanish in low-bandwidth mode', async ({
    page,
  }) => {
    await page.goto('/login')
    await expect(page.locator('[data-backdrop]')).toHaveAttribute('data-motion', 'float')
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.reload()
    await expect(page.locator('[data-backdrop]')).toHaveAttribute('data-motion', 'still')
    expect(
      await page
        .locator('[data-orb="1"]')
        .evaluate((element) => getComputedStyle(element).animationName),
    ).toBe('none')
    await page.evaluate(() => {
      window.localStorage.setItem('shromikai.lowbw', 'true')
    })
    await page.reload()
    await expect(page.locator('[data-backdrop]')).toHaveCount(0)
  })

  test('report pictures of the C1 shell and primitives', async ({ page }) => {
    test.skip(!shots, 'set P9_SHOTS=C1 to take the report pictures')
    test.setTimeout(240_000)
    await page.goto('/?scene=lite')
    await page.evaluate(() => document.fonts.ready)
    await shot(page, 'landing-lite')
    await page.goto('/login')
    await shot(page, 'login')
    await page.goto('/docs')
    await page.waitForLoadState('networkidle')
    await shot(page, 'docs')
    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text('bn', 'upload.title') })).toBeVisible()
    await shot(page, 'upload')
    await analyseSample(page, 'bn')
    await shot(page, 'results')
  })

  test('report pictures in the light theme', async ({ page }) => {
    test.skip(!shots, 'set P9_SHOTS=C1 to take the report pictures')
    await inTheme(page, 'light')
    await page.goto('/login')
    await shot(page, 'login-light')
    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text('bn', 'upload.title') })).toBeVisible()
    await shot(page, 'upload-light')
  })

  test('report picture of the cinematic hero heading', async ({ page }, info) => {
    test.skip(!shots, 'set P9_SHOTS=C1 to take the report pictures')
    test.skip(info.project.name !== 'desktop-chromium', 'desktop width only')
    await page.goto('/?scene=lite')
    await page.evaluate(() => document.fonts.ready)
    await page.locator('#hero-title').scrollIntoViewIfNeeded()
    await shot(page, 'landing-hero-type')
  })
})
