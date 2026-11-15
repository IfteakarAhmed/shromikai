import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { inLocale, text } from './support'

/**
 * Screenshots for the phase report (docs/img/p9/). Run only on request:
 *   P9_SHOTS=A npx playwright test e2e/screenshots.spec.ts
 * The prefix names the increment. Every picture is of the real app on the real recorded backend.
 */
const prefix = process.env['P9_SHOTS']
const folder = resolve(process.cwd(), '..', 'docs', 'img', 'p9')

test.describe('screenshots', () => {
  test.skip(prefix === undefined, 'set P9_SHOTS=A (or B, C) to take the report pictures')

  async function shot(page: Page, name: string, fullPage = false) {
    mkdirSync(folder, { recursive: true })
    const width = page.viewportSize()?.width ?? 0
    await page.screenshot({
      path: resolve(folder, `${prefix ?? 'X'}-${name}-${width}.png`),
      fullPage,
    })
  }

  test.beforeEach(async ({ page }) => {
    await inLocale(page, 'bn')
  })

  test('increment pictures: landing, upload, progress, results, uncertain card and citation sheet', async ({
    page,
  }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1500) // let the 3D scene fade in where the browser allows it
    await shot(page, 'landing')
    await shot(page, 'landing-full', true)

    await page.goto('/login')
    await shot(page, 'login')

    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text('bn', 'upload.title') })).toBeVisible()
    await shot(page, 'upload')

    // Hold the live stream half way, with the real events that were already sent, so the progress
    // screen can be photographed.
    let first = true
    await page.route('**/api/v1/analyses/*/events', async (route) => {
      if (!first) {
        await new Promise((done) => setTimeout(done, 3500))
        await route.continue()
        return
      }
      first = false
      const real = await route.fetch()
      const frames = (await real.text()).split('\n\n').filter((frame) => frame.includes('event:'))
      await route.fulfill({
        response: real,
        body: `${frames.slice(0, 3).join('\n\n')}\n\n`,
      })
    })
    await page.getByRole('button', { name: text('bn', 'upload.sample') }).click()
    await expect(page.getByRole('progressbar')).toBeVisible()
    await page.waitForTimeout(1200)
    await shot(page, 'progress')

    await expect(page.getByRole('heading', { name: text('bn', 'results.title') })).toBeVisible({
      timeout: 60_000,
    })
    await page.waitForTimeout(300)
    await shot(page, 'results-top')

    const cards = page.locator('article')
    await cards.first().scrollIntoViewIfNeeded()
    await shot(page, 'results-cards')

    const uncertain = cards.filter({ hasText: text('bn', 'verification.sentForReview') }).first()
    await uncertain.scrollIntoViewIfNeeded()
    await shot(page, 'results-uncertain')

    await cards
      .filter({ hasText: text('bn', 'verification.verified') })
      .first()
      .getByRole('button', { name: /খুলুন/ })
      .first()
      .click()
    await expect(page.getByRole('dialog').locator('mark.quote').first()).toBeVisible()
    await page.waitForTimeout(300)
    await shot(page, 'citation-sheet')
  })

  test('increment pictures: English and light theme', async ({ page }) => {
    await inLocale(page, 'en')
    await page.addInitScript(() => {
      window.localStorage.setItem('shromikai.theme', 'light')
    })
    await page.goto('/')
    await page.waitForLoadState('networkidle')
    await shot(page, 'landing-en-light')
  })
})
