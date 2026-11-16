import { expect, test } from '@playwright/test'
import { analyseSample, inLocale, LOCALES, text } from './support'

/** The demo without rules (`DEMO_RULES=off`): the notice that no active rule applied stays visible. */
for (const locale of LOCALES) {
  test(`FR-71 rules off (${locale}): the proposals tab says no active rule was applied @rules-off`, async ({
    page,
  }) => {
    await inLocale(page, locale)
    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
    await analyseSample(page, locale)

    await expect(
      page.getByText(text(locale, 'warningCodes.counter-offer-unchecked')).first(),
    ).toBeVisible()
    await page.getByRole('tab', { name: text(locale, 'tabs.proposals') }).click()
    await expect(page.getByText(text(locale, 'warningCodes.counter-offer-unchecked'))).toBeVisible()
  })
}
