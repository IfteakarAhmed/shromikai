import { expect, test } from '@playwright/test'
import { analyseSample, inLocale, LOCALES, text } from './support'

/**
 * The default demo (legal rules on): the rules produce findings of their own. They come from the
 * rule engine, are VERIFIED with a statute citation and say so (FR-36, R-01). The same finding
 * titles are not asserted; the rule findings are recognised by their source label.
 */
for (const locale of LOCALES) {
  test.describe(`rules on (${locale})`, () => {
    test.beforeEach(async ({ page }) => {
      await inLocale(page, locale)
    })

    test('FR-36 FR-70 rule findings are labelled as a fixed legal limit, verified and cited', async ({
      page,
    }) => {
      await page.goto('/demo-user')
      await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
      await analyseSample(page, locale)

      const ruleCards = page
        .locator('article')
        .filter({ has: page.getByTestId('finding-source') })
        .filter({ hasText: text(locale, 'finding.source.RULE_ENGINE') })
      // The sample breaks four limits: daily hours, overtime rate, weekly rest and festival holidays.
      await expect(ruleCards).toHaveCount(4)
      for (let i = 0; i < 4; i += 1) {
        const card = ruleCards.nth(i)
        await expect(card.getByText(text(locale, 'verification.verified'))).toBeVisible()
        await expect(card.getByRole('button', { name: /(ধারা|ection) \S+/ }).first()).toBeVisible()
      }
      // No uncertain card and no "no active rule" notice while the rules are on.
      await expect(page.getByText(text(locale, 'verification.sentForReview'))).toHaveCount(0)
    })

    test('FR-71 the proposals tab does not claim that no rule applied', async ({ page }) => {
      await page.goto('/demo-user')
      await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
      await analyseSample(page, locale)
      await page.getByRole('tab', { name: text(locale, 'tabs.proposals') }).click()
      await expect(
        page.getByText(text(locale, 'warningCodes.counter-offer-unchecked')),
      ).toHaveCount(0)
    })
  })
}
