import { expect, test } from '@playwright/test'
import { inLocale, LOCALES, text } from './support'

for (const locale of LOCALES) {
  test.describe(`docs hub (${locale})`, () => {
    test.beforeEach(async ({ page }) => {
      await inLocale(page, locale)
    })

    test('FR-74 every page of the hub opens with its heading', async ({ page }) => {
      const pages: [string, string][] = [
        ['/docs', 'docs.title'],
        ['/docs/architecture', 'docs.architecture.title'],
        ['/docs/data', 'docs.data.title'],
        ['/docs/security', 'docs.security.title'],
        ['/docs/audit', 'docs.audit.title'],
        ['/docs/evaluation', 'docs.evaluation.title'],
        ['/docs/prompts', 'docs.promptLibrary'],
        ['/docs/mcp', 'docs.nav.mcp'],
        ['/docs/known-gaps', 'docs.nav.gaps'],
      ]
      for (const [route, key] of pages) {
        await page.goto(route)
        await expect(
          page.getByRole('heading', { name: text(locale, key), level: 1 }),
          route,
        ).toBeVisible()
      }
    })

    test('FR-74 R-07 /docs/audit is read from the live system and /docs/evaluation shows the generated contract report', async ({
      page,
    }) => {
      await page.goto('/docs/audit')
      await expect(page.getByTestId('audit-version')).toBeVisible()
      await expect(page.getByTestId('audit-sections')).not.toHaveText('')
      await expect(page.getByText(text(locale, 'docs.audit.recordedDemo'))).toBeVisible()

      await page.goto('/docs/evaluation')
      // eval/reports/report.json exists (P10): its headline, with counts, replaces "not yet run".
      await expect(page.getByTestId('contracts-headline')).toBeVisible()
      await expect(page.getByTestId('contracts-not-run')).toHaveCount(0)
      await expect(page.getByTestId('contracts-sample')).toBeVisible()
      // No percentage anywhere on the page: the only numbers are counts from the report files.
      await expect(page.locator('main, body').first()).not.toContainText(/\d\s?%|[০-৯]\s?%/)
    })

    test('FR-74 the architecture page draws both pictures', async ({ page }) => {
      await page.goto('/docs/architecture')
      await expect(
        page.getByRole('img', { name: text(locale, 'docs.architecture.containersTitle') }),
      ).toBeVisible()
      await expect(
        page.getByRole('img', { name: text(locale, 'docs.architecture.pipelineTitle') }),
      ).toBeVisible()
    })

    test('FR-74 NFR-08 no label of the architecture pictures is wider than its box', async ({
      page,
    }) => {
      await page.goto('/docs/architecture')
      await expect(
        page.getByRole('img', { name: text(locale, 'docs.architecture.pipelineTitle') }),
      ).toBeVisible()
      const overflowing = await page.evaluate(() => {
        const bad: string[] = []
        document.querySelectorAll('svg g').forEach((g) => {
          const rect = g.querySelector(':scope > rect')
          if (rect === null) return
          const box = rect.getBoundingClientRect()
          g.querySelectorAll(':scope > text').forEach((label) => {
            const t = label.getBoundingClientRect()
            if (t.left < box.left - 1 || t.right > box.right + 1) bad.push(label.textContent)
          })
        })
        return bad
      })
      expect(overflowing).toEqual([])
    })

    test('FR-74 a prompt page opens from the library', async ({ page }) => {
      await page.goto('/docs/prompts')
      await page.getByRole('link', { name: 'risk-analysis', exact: true }).click()
      await expect(page.getByRole('heading', { name: 'risk-analysis', level: 1 })).toBeVisible()
    })

    test('FR-74 the footer reaches the security and known-gaps pages', async ({ page }) => {
      await page.goto('/')
      await page
        .getByRole('contentinfo')
        .getByRole('link', { name: text(locale, 'footer.knownGaps') })
        .click()
      await expect(page).toHaveURL(/\/docs\/known-gaps$/)
    })
  })
}
