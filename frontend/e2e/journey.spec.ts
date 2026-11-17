import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { inLocale, LOCALES, openDetails, openResult, text, toNumber } from './support'

/**
 * The worker journey (C3) on the real recorded backend: three upload steps, the lit progress, the
 * plain summary with its count from the API, the seal only after verification, next steps that
 * lead to real parts of the result, compact cards whose চিপ and review message stay in view.
 *
 * Pictures: P9_SHOTS=C3 npx playwright test e2e/journey.spec.ts
 *           P9_SHOTS=C3 DEMO_RULES=off npx playwright test e2e/journey.spec.ts   (ticket, uncertain)
 */
const shots = process.env['P9_SHOTS'] === 'C3'
const folder = resolve(process.cwd(), '..', 'docs', 'img', 'p9')

async function shot(page: Page, name: string, theme: string) {
  if (!shots) {
    return
  }
  mkdirSync(folder, { recursive: true })
  const width = page.viewportSize()?.width ?? 0
  await page.screenshot({ path: resolve(folder, `C3-${name}-${theme}-${String(width)}.png`) })
}

async function inTheme(page: Page, theme: 'dark' | 'light') {
  await page.addInitScript((value) => {
    window.localStorage.setItem('shromikai.theme', value)
  }, theme)
}

/** The sum of the counts printed beside the severity tags (the API's findingsBySeverity). */
async function severityCounts(page: Page, locale: 'bn' | 'en') {
  const row = page.getByRole('list', { name: text(locale, 'severity.label') })
  const items = await row.getByRole('listitem').allTextContents()
  const count = (label: string) =>
    toNumber(items.find((item) => item.startsWith(label))?.slice(label.length) ?? '')
  return {
    critical: count(text(locale, 'severity.critical')),
    high: count(text(locale, 'severity.high')),
  }
}

for (const locale of LOCALES) {
  test.describe(`worker journey (${locale})`, () => {
    test.beforeEach(async ({ page }) => {
      await inLocale(page, locale)
    })

    test('FR-69 the upload is three numbered steps: the contract, the side, start', async ({
      page,
    }) => {
      await page.goto('/demo-user')
      const steps = page.getByRole('group', { name: text(locale, 'upload.steps.label') })
      await expect(steps).toBeVisible()
      await expect(steps.getByRole('heading', { level: 2 })).toHaveText([
        text(locale, 'upload.steps.contract'),
        text(locale, 'upload.steps.side'),
        text(locale, 'upload.steps.start'),
      ])
    })

    test('FR-70 R-01 the plain summary counts big problems from the API; the seal follows verification', async ({
      page,
    }) => {
      await openResult(page, locale)
      const { critical, high } = await severityCounts(page, locale)
      const summary = page.locator('#plain-summary')
      if (critical + high > 0) {
        expect(toNumber((await summary.textContent()) ?? '')).toBe(critical + high)
      } else {
        await expect(summary).toHaveText(text(locale, 'results.summary.noBig'))
      }
      // The seal says verification finished, with the API's verified count.
      const seal = page.getByRole('status', { name: text(locale, 'results.seal.title') })
      await expect(seal).toBeVisible()
      const total = toNumber((await page.getByTestId('total-findings').textContent()) ?? '')
      const uncertain = toNumber((await page.getByTestId('uncertain-findings').textContent()) ?? '')
      const numbers = ((await seal.textContent()) ?? '').match(/[\d০-৯]+/g)?.map(toNumber) ?? []
      expect(numbers[0]).toBeLessThanOrEqual(total)
      if (uncertain > 0) {
        expect(numbers[1]).toBe(uncertain)
      }
      // R-12: the result still says it is not legal advice.
      await expect(page.getByText(/এটা আইনি পরামর্শ নয়|not legal advice/i).first()).toBeVisible()
    })

    test('FR-70 the next steps lead to real parts of the result', async ({ page }) => {
      await openResult(page, locale)
      const steps = page.getByRole('region', { name: text(locale, 'results.next.title') })
      await expect(steps).toBeVisible()
      await steps
        .getByRole('link')
        .filter({ hasText: text(locale, 'results.next.memo') })
        .click()
      await expect(page.getByRole('tab', { name: text(locale, 'tabs.memo') })).toHaveAttribute(
        'aria-selected',
        'true',
      )
    })

    test('FR-70 a compact card keeps marks and chips in view and folds the rest', async ({
      page,
    }) => {
      await openResult(page, locale)
      const card = page
        .locator('article')
        .filter({ hasText: text(locale, 'verification.verified') })
        .first()
      await expect(card.getByRole('button', { name: /খুলুন|Open/ }).first()).toBeVisible()
      const why = card.getByRole('button', { name: text(locale, 'finding.why') })
      await expect(why).toBeHidden()
      await openDetails(card, locale)
      await expect(why).toBeVisible()
    })

    test('R-01 an uncertain finding stays labelled, with the review message, and leads to the ticket @rules-off', async ({
      page,
    }) => {
      await openResult(page, locale)
      const card = page
        .locator('article')
        .filter({ hasText: text(locale, 'verification.sentForReview') })
        .first()
      await expect(card.getByText(text(locale, 'verification.uncertain'))).toBeVisible()
      await expect(card.getByText(text(locale, 'verification.sentForReview'))).toBeVisible()
      await expect(card.getByRole('button', { name: /খুলুন|Open/ })).toHaveCount(0)
      const steps = page.getByRole('region', { name: text(locale, 'results.next.title') })
      await expect(steps.locator('a[href^="/app/tickets/"]')).toHaveCount(1)
    })
  })
}

for (const theme of ['dark', 'light'] as const) {
  test(`report pictures of the worker journey (${theme})`, async ({ page }) => {
    test.skip(!shots, 'set P9_SHOTS=C3 to take the report pictures')
    test.skip(process.env['DEMO_RULES'] === 'off', 'the default-mode pictures')
    test.setTimeout(300_000)
    await inLocale(page, 'bn')
    await inTheme(page, theme)
    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text('bn', 'upload.title') })).toBeVisible()
    await page.locator('#step-contract').scrollIntoViewIfNeeded()
    await shot(page, 'upload', theme)

    // Hold the live stream part way, with the real events already sent, to photograph progress.
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
      await route.fulfill({ response: real, body: `${frames.slice(0, 3).join('\n\n')}\n\n` })
    })
    await page.getByRole('button', { name: text('bn', 'upload.sample') }).click()
    await expect(page.locator('[data-stage]').first()).toBeVisible()
    await page.waitForTimeout(1200)
    await shot(page, 'progress', theme)

    await expect(page.getByRole('heading', { name: text('bn', 'results.title') })).toBeVisible({
      timeout: 60_000,
    })
    await page.waitForTimeout(1600)
    await shot(page, 'results-top', theme)
    await page
      .getByRole('region', { name: text('bn', 'results.next.title') })
      .scrollIntoViewIfNeeded()
    await shot(page, 'next-steps', theme)

    const card = page.locator('article').first()
    await card.scrollIntoViewIfNeeded()
    await shot(page, 'card-compact', theme)
    await openDetails(card, 'bn')
    await page.waitForTimeout(200)
    await shot(page, 'card-details', theme)

    await card.getByRole('button', { name: /খুলুন/ }).first().click()
    await expect(page.getByRole('dialog').locator('mark.quote').first()).toBeVisible()
    await page.waitForTimeout(800)
    await shot(page, 'citation-sheet', theme)
    await page.keyboard.press('Escape')
    await card.getByRole('button', { name: text('bn', 'finding.why') }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.waitForTimeout(800)
    await shot(page, 'why-sheet', theme)
    await page.keyboard.press('Escape')

    const base = new URL(page.url()).pathname
    for (const tab of ['proposals', 'duties', 'memo'] as const) {
      await page.goto(`${base}?tab=${tab}`)
      await expect(page.getByRole('tab', { name: text('bn', `tabs.${tab}`) })).toHaveAttribute(
        'aria-selected',
        'true',
      )
      await page.waitForTimeout(500)
      await page.getByRole('tabpanel').scrollIntoViewIfNeeded()
      await shot(page, tab, theme)
    }

    await page.goto('/app/profile')
    await page.waitForLoadState('networkidle')
    await shot(page, 'profile', theme)

    await page.goto('/app/compare')
    await page.getByRole('button', { name: text('bn', 'compare.start') }).click()
    await expect(page.getByTestId('compare-summary')).toBeVisible({ timeout: 90_000 })
    await page.waitForTimeout(800)
    await shot(page, 'compare', theme)
  })

  test(`report pictures of the uncertain card and the ticket (${theme}) @rules-off`, async ({
    page,
  }) => {
    test.skip(!shots, 'set P9_SHOTS=C3 to take the report pictures')
    test.setTimeout(300_000)
    await inLocale(page, 'bn')
    await inTheme(page, theme)
    await openResult(page, 'bn')
    await page.waitForTimeout(1600)
    await shot(page, 'results-top-uncertain', theme)
    const card = page
      .locator('article')
      .filter({ hasText: text('bn', 'verification.sentForReview') })
      .first()
    await card.scrollIntoViewIfNeeded()
    await shot(page, 'card-uncertain', theme)
    await card.getByRole('link', { name: text('bn', 'finding.ticketLink') }).click()
    await expect(page.getByText(text('bn', 'ticket.status.OPEN')).first()).toBeVisible()
    await page.waitForTimeout(500)
    await shot(page, 'ticket', theme)
  })
}
