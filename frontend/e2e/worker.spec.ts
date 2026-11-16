import { expect, test } from '@playwright/test'
import { inLocale, LOCALES, text, toNumber } from './support'

for (const locale of LOCALES) {
  test.describe(`worker flow (${locale})`, () => {
    test.beforeEach(async ({ page }) => {
      await inLocale(page, locale)
    })

    test('FR-68 FR-69 FR-70 /demo-user: sample, live progress, verified and uncertain results, citation sheet @rules-off', async ({
      page,
    }) => {
      await page.goto('/demo-user')
      await expect(page).toHaveURL(/\/app$/)
      await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
      // The recorded demo says so before anything is analysed (R-12).
      await expect(page.getByText(text(locale, 'common.recorded')).first()).toBeVisible()

      await page.getByRole('button', { name: text(locale, 'upload.sample') }).click()

      // The progress screen follows the real stages and then gives way to the result.
      await expect(page.getByRole('progressbar')).toBeVisible()
      await expect(page.getByRole('heading', { name: text(locale, 'results.title') })).toBeVisible({
        timeout: 60_000,
      })

      const total = toNumber((await page.getByTestId('total-findings').textContent()) ?? '')
      const uncertain = toNumber((await page.getByTestId('uncertain-findings').textContent()) ?? '')
      expect(total).toBeGreaterThanOrEqual(3)
      expect(uncertain).toBeGreaterThanOrEqual(1)

      // Verified cards carry citation chips; an uncertain card says it went to human review and
      // has no chip (R-01).
      await expect(
        page.locator('article', { hasText: text(locale, 'verification.verified') }).first(),
      ).toBeVisible()
      const uncertainCard = page
        .locator('article')
        .filter({ hasText: text(locale, 'verification.sentForReview') })
        .first()
      await expect(uncertainCard).toBeVisible()
      await expect(
        uncertainCard.getByRole('button', { name: /(ধারা|section) \S+/i }).filter({ hasText: /./ }),
      ).toHaveCount(0)
      await expect(page.getByText(text(locale, 'results.sampleLabel'))).toBeVisible()

      // A chip opens the exact section text with the quoted words highlighted.
      const verifiedCard = page
        .locator('article')
        .filter({ hasText: text(locale, 'verification.verified') })
        .first()
      await verifiedCard
        .getByRole('button', { name: /খুলুন|Open/ })
        .first()
        .click()
      const sheet = page.getByRole('dialog')
      await expect(sheet).toBeVisible()
      await expect(sheet.locator('mark.quote').first()).toBeVisible()
      const official = sheet.getByRole('link', { name: text(locale, 'citationSheet.official') })
      await expect(official).toHaveAttribute('target', '_blank')
      await expect(official).toHaveAttribute('rel', /noopener/)
      await page.keyboard.press('Escape')
      await expect(sheet).toBeHidden()

      // The disclaimer is on the result (FR-77).
      await expect(page.getByText(/এটা আইনি পরামর্শ নয়|not legal advice/i).first()).toBeVisible()
    })

    test('FR-68 a guest runs the sample from the landing page with no account', async ({
      page,
    }) => {
      await page.goto('/')
      await page.getByRole('link', { name: text(locale, 'hero.sample') }).click()

      await expect(page.getByRole('heading', { name: text(locale, 'results.title') })).toBeVisible({
        timeout: 60_000,
      })
      await expect(page.getByText(text(locale, 'nav.guestAs'))).toBeVisible()

      // R-02: in a real browser, nothing but the device token and preferences is stored.
      const stored = await page.evaluate(async () => ({
        local: Object.keys(window.localStorage),
        session: Object.keys(window.sessionStorage).length,
        databases: (await window.indexedDB.databases()).length,
        caches: await caches.keys(),
        workers: (await navigator.serviceWorker.getRegistrations()).length,
        dump: JSON.stringify(window.localStorage),
      }))
      for (const key of stored.local) {
        expect([
          'shromikai.locale',
          'shromikai.side',
          'shromikai.lowbw',
          'shromikai.theme',
          'shromikai.device',
        ]).toContain(key)
      }
      expect(stored.session).toBe(0)
      expect(stored.databases).toBe(0)
      expect(stored.caches).toEqual([])
      expect(stored.workers).toBe(0)
      expect(stored.dump).not.toMatch(/SECTION|BLA-2006|findings|envelope/)
    })

    test('FR-69 pasted text that was not recorded is explained, not shown as a crash', async ({
      page,
    }) => {
      await page.goto('/demo-user')
      // In the recorded demo only the sample can be analysed; the controls say so.
      await expect(page.getByText(text(locale, 'upload.recordedOnly'))).toBeVisible()
      await expect(page.getByRole('button', { name: text(locale, 'upload.submit') })).toBeDisabled()
    })
  })
}
