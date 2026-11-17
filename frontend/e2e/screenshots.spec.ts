import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { inLocale, openDetails, text } from './support'

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

  test('increment pictures: landing, upload, progress, results, uncertain card and citation sheet @rules-off', async ({
    page,
  }) => {
    test.skip(prefix === 'C', 'increment C has its own pictures')
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

  test('increment pictures: English and light theme @rules-off', async ({ page }) => {
    test.skip(prefix === 'C', 'increment C has its own pictures')
    await inLocale(page, 'en')
    await page.addInitScript(() => {
      window.localStorage.setItem('shromikai.theme', 'light')
    })
    await page.goto('/')
    await page.waitForLoadState('networkidle')
    await shot(page, 'landing-en-light')
  })

  test('increment B pictures: why drawer, redline, duties, memo, compare and the ticket @rules-off', async ({
    page,
  }) => {
    test.skip(prefix !== 'B', 'only for increment B')
    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text('bn', 'upload.title') })).toBeVisible()
    await page.getByRole('button', { name: text('bn', 'upload.sample') }).click()
    await expect(page.getByRole('heading', { name: text('bn', 'results.title') })).toBeVisible({
      timeout: 60_000,
    })

    const firstCard = page.locator('article').first()
    await openDetails(firstCard, 'bn')
    await firstCard.getByRole('button', { name: text('bn', 'finding.why') }).click()
    await expect(page.getByRole('dialog').getByText(text('bn', 'why.checksTitle'))).toBeVisible()
    await page.waitForTimeout(400)
    await shot(page, 'why-verified')
    await page.keyboard.press('Escape')

    const uncertainCard = page
      .locator('article')
      .filter({ hasText: text('bn', 'verification.sentForReview') })
      .first()
    await openDetails(uncertainCard, 'bn')
    await uncertainCard.getByRole('button', { name: text('bn', 'finding.why') }).click()
    await expect(page.getByRole('dialog').getByText(text('bn', 'why.uncertainTitle'))).toBeVisible()
    await page.waitForTimeout(400)
    await shot(page, 'why-uncertain')
    await page.keyboard.press('Escape')

    await page.getByRole('tab', { name: text('bn', 'tabs.proposals') }).click()
    await page.locator('del').first().scrollIntoViewIfNeeded()
    await shot(page, 'redline')
    await page.getByRole('tab', { name: text('bn', 'tabs.duties') }).click()
    await page.getByRole('checkbox').first().check()
    await shot(page, 'duties')
    await page.getByRole('tab', { name: text('bn', 'tabs.memo') }).click()
    await page.locator('.memo-print').scrollIntoViewIfNeeded()
    await shot(page, 'memo')
    await page.emulateMedia({ media: 'print' })
    await page.locator('.memo-print').scrollIntoViewIfNeeded()
    await shot(page, 'memo-print')
    await page.emulateMedia({ media: 'screen' })

    await page.getByRole('tab', { name: text('bn', 'tabs.risks') }).click()
    await page
      .getByRole('link', { name: text('bn', 'finding.ticketLink') })
      .first()
      .click()
    await expect(page.getByText(text('bn', 'ticket.status.OPEN')).first()).toBeVisible()
    await shot(page, 'ticket')

    await page.goto('/app/compare')
    await shot(page, 'compare-start')
    await page.getByRole('button', { name: text('bn', 'compare.start') }).click()
    await expect(page.getByTestId('compare-summary')).toBeVisible({ timeout: 90_000 })
    await page.waitForTimeout(400)
    await shot(page, 'compare')
    await page.getByTestId('compare-summary').scrollIntoViewIfNeeded()
    await page.evaluate(() => {
      window.scrollBy(0, 500)
    })
    await shot(page, 'compare-claims')
  })

  test('increment C pictures: admin console and docs hub', async ({ page }) => {
    test.skip(prefix !== 'C', 'only for increment C')
    const nav = (key: string) =>
      page
        .getByRole('navigation', { name: text('bn', 'admin.nav.label') })
        .getByRole('link', { name: text('bn', `admin.nav.${key}`) })

    // A worker's sample run sends its uncertain findings to the review queue.
    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text('bn', 'upload.title') })).toBeVisible()
    await page.getByRole('button', { name: text('bn', 'upload.sample') }).click()
    await expect(page.getByRole('heading', { name: text('bn', 'results.title') })).toBeVisible({
      timeout: 60_000,
    })
    const analysisId = /\/app\/analyses\/([0-9a-f-]{36})/.exec(page.url())?.[1] ?? ''

    await page.goto('/demo-admin')
    await expect(page.getByTestId('kpi-value').first()).toBeVisible()
    await page.waitForTimeout(500)
    await shot(page, 'admin-overview')

    await nav('review').click()
    await expect(
      page.getByRole('heading', { name: text('bn', 'admin.review.title'), level: 2 }),
    ).toBeVisible()
    await page.waitForTimeout(500)
    await shot(page, 'admin-review-queue')
    const open = page.getByRole('button', { name: new RegExp(text('bn', 'admin.review.open')) })
    const link = page.getByRole('link', { name: new RegExp(text('bn', 'admin.review.open')) })
    await ((await open.count()) > 0 ? open : link).first().click()
    await page.getByRole('button', { name: text('bn', 'admin.review.claimButton') }).click()
    await expect(page.getByText(text('bn', 'admin.review.decide.claimed'))).toBeVisible()
    await page.getByRole('radio', { name: text('bn', 'admin.review.decide.REJECTED') }).check()
    await page.getByRole('button', { name: text('bn', 'admin.review.decide.submit') }).click()
    await expect(page.getByText(text('bn', 'admin.review.decide.noteRequired'))).toBeVisible()
    await page.getByLabel(text('bn', 'admin.review.decide.note')).scrollIntoViewIfNeeded()
    await shot(page, 'admin-review-note-required')
    await page.getByRole('button', { name: text('bn', 'admin.review.release') }).click()

    await nav('playbooks').click()
    await page.getByRole('button', { name: text('bn', 'admin.playbooks.new') }).click()
    await page.waitForTimeout(300)
    await shot(page, 'admin-playbook-form')

    await nav('keys').click()
    await page.getByLabel(text('bn', 'admin.keys.name')).fill('ডেমো কী')
    await page.getByRole('button', { name: text('bn', 'admin.keys.submit') }).click()
    await expect(page.getByTestId('api-key-secret')).toBeVisible()
    await shot(page, 'admin-api-key-secret')
    await page.getByRole('button', { name: text('bn', 'admin.keys.done') }).click()

    await nav('usage').click()
    await expect(page.locator('.recharts-responsive-container').first()).toBeVisible({
      timeout: 20_000,
    })
    await page.waitForTimeout(800)
    await shot(page, 'admin-usage')

    await nav('trace').click()
    await page.getByLabel(text('bn', 'admin.trace.idLabel')).fill(analysisId)
    await page.getByRole('button', { name: text('bn', 'admin.trace.open') }).click()
    await expect(page.getByRole('table')).toBeVisible()
    await shot(page, 'admin-trace')

    // The default demo: the rules produce findings of their own, labelled as fixed legal limits.
    await page.goto('/demo-user')
    await page.getByRole('button', { name: text('bn', 'upload.sample') }).click()
    await expect(page.getByRole('heading', { name: text('bn', 'results.title') })).toBeVisible({
      timeout: 60_000,
    })
    const ruleCard = page
      .locator('article')
      .filter({ hasText: text('bn', 'finding.source.RULE_ENGINE') })
      .first()
    await ruleCard.scrollIntoViewIfNeeded()
    await shot(page, 'results-rule-finding')

    for (const [route, name] of [
      ['/docs', 'docs-index'],
      ['/docs/architecture', 'docs-architecture'],
      ['/docs/audit', 'docs-audit'],
      ['/docs/evaluation', 'docs-evaluation'],
      ['/docs/data', 'docs-data'],
      ['/docs/security', 'docs-security'],
    ] as const) {
      await page.goto(route)
      await page.waitForLoadState('networkidle')
      await page.waitForTimeout(500)
      await shot(page, name)
    }
  })
})
