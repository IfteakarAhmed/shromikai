import { expect, test } from '@playwright/test'
import { inLocale, LOCALES, text, toNumber } from './support'

for (const locale of LOCALES) {
  test(`FR-72 compare (${locale}): rejected baseline claims are marked and counted from the response`, async ({
    page,
  }, info) => {
    await inLocale(page, locale)
    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
    await page.goto('/app/compare')
    await page.getByRole('button', { name: text(locale, 'compare.start') }).click()

    const summary = page.getByTestId('compare-summary')
    await expect(summary).toBeVisible({ timeout: 90_000 })
    const [total, rejected] = (await summary.textContent())?.match(/[০-৯\d]+/g)?.map(toNumber) ?? []
    expect(total).toBeGreaterThan(0)
    expect(rejected).toBeGreaterThan(0)

    // Phones show one column at a time, behind tabs.
    const narrow = (info.project.use.viewport?.width ?? 1280) < 768
    if (narrow) {
      await expect(
        page.getByRole('tab', { name: text(locale, 'compare.tabBaseline') }),
      ).toBeVisible()
    }
    const baseline = page.getByRole('region', { name: text(locale, 'compare.baselineTitle') })
    await expect(baseline).toBeVisible()
    // The number in the sentence is the number of crosses in the column.
    await expect(
      baseline.getByText(text(locale, 'compare.claimRejected'), { exact: true }),
    ).toHaveCount(rejected)
    // R-01 / ADR-0022: a claim is not a citation: no chip, no link, no verified mark.
    await expect(baseline.getByRole('button')).toHaveCount(0)
    await expect(baseline.getByRole('link')).toHaveCount(0)
    await expect(
      baseline.getByText(text(locale, 'verification.verified'), { exact: true }),
    ).toHaveCount(0)
    await expect(baseline.getByText(text(locale, 'compare.notACitation')).first()).toBeVisible()
    await expect(baseline.getByText(text(locale, 'compare.scripted'))).toBeVisible()

    if (narrow) {
      await page.getByRole('tab', { name: text(locale, 'compare.tabOurs') }).click()
    }
    const ours = page.getByRole('region', { name: text(locale, 'compare.oursTitle') })
    await expect(ours).toBeVisible()
    await expect(ours.getByText(text(locale, 'verification.verified')).first()).toBeVisible()
  })
}
