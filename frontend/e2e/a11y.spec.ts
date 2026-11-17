import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { analyseSample, inLocale, LOCALES, openDetails, text } from './support'

async function expectNoSeriousViolations(page: Page, where: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const serious = results.violations.filter(
    (violation) => violation.impact === 'serious' || violation.impact === 'critical',
  )
  expect(
    serious.map(
      (v) =>
        `${v.id} (${v.impact}): ${v.nodes
          .slice(0, 3)
          .map((n) => n.target.join(' '))
          .join(' | ')}`,
    ),
    `axe on ${where}`,
  ).toEqual([])
}

/** The key of each admin address in `admin.nav`. */
const NAV: Record<string, string> = {
  '/admin/review': 'review',
  '/admin/playbooks': 'playbooks',
  '/admin/api-keys': 'keys',
  '/admin/usage': 'usage',
  '/admin/traces': 'trace',
}

async function inTheme(page: Page, theme: 'dark' | 'light') {
  await page.addInitScript((value) => {
    window.localStorage.setItem('shromikai.theme', value)
  }, theme)
}

for (const locale of LOCALES) {
  for (const theme of ['dark', 'light'] as const) {
    test.describe(`axe (${locale}, ${theme})`, () => {
      test.beforeEach(async ({ page }) => {
        await inLocale(page, locale)
        await inTheme(page, theme)
      })

      for (const route of [
        '/',
        '/login',
        '/docs',
        '/docs/architecture',
        '/docs/data',
        '/docs/security',
        '/docs/audit',
        '/docs/evaluation',
        '/docs/prompts',
        '/docs/prompts/risk-analysis',
        '/docs/mcp',
        '/docs/known-gaps',
      ]) {
        test(`NFR-08 ${route}`, async ({ page }) => {
          await page.goto(route)
          await page.waitForLoadState('networkidle')
          await expectNoSeriousViolations(page, route)
        })
      }

      test('NFR-08 /app and the result with its citation sheet', async ({ page }) => {
        await page.goto('/demo-user')
        await expect(
          page.getByRole('heading', { name: text(locale, 'upload.title') }),
        ).toBeVisible()
        await expectNoSeriousViolations(page, '/app')

        await analyseSample(page, locale)
        await expectNoSeriousViolations(page, 'results')

        await page
          .locator('article')
          .filter({ hasText: text(locale, 'verification.verified') })
          .first()
          .getByRole('button', { name: /খুলুন|Open/ })
          .first()
          .click()
        await expect(page.getByRole('dialog')).toBeVisible()
        await expectNoSeriousViolations(page, 'citation sheet')
        await page.keyboard.press('Escape')

        const firstCard = page.locator('article').first()
        await openDetails(firstCard, locale)
        await firstCard.getByRole('button', { name: text(locale, 'finding.why') }).click()
        await expect(page.getByRole('dialog')).toBeVisible()
        await expectNoSeriousViolations(page, 'why drawer')
        await page.keyboard.press('Escape')

        for (const tab of ['proposals', 'duties', 'memo']) {
          await page.getByRole('tab', { name: text(locale, `tabs.${tab}`) }).click()
          await expectNoSeriousViolations(page, `results tab ${tab}`)
        }
      })

      test('NFR-08 the admin console', async ({ page }) => {
        await page.goto('/demo-admin')
        await expect(page.getByRole('heading', { name: text(locale, 'admin.title') })).toBeVisible()
        await expect(page.getByTestId('kpi-value').first()).toBeVisible()
        await expectNoSeriousViolations(page, '/admin')
        for (const route of [
          '/admin/review',
          '/admin/playbooks',
          '/admin/api-keys',
          '/admin/usage',
          '/admin/traces',
        ]) {
          await page
            .getByRole('navigation', { name: text(locale, 'admin.nav.label') })
            .getByRole('link', { name: text(locale, `admin.nav.${NAV[route] ?? 'overview'}`) })
            .click()
          await page.waitForLoadState('networkidle')
          await expect(page).toHaveURL(new RegExp(`${route}$`))
          await expectNoSeriousViolations(page, route)
        }
      })

      test('NFR-08 the playbook form', async ({ page }) => {
        await page.goto('/demo-hr')
        await expect(
          page.getByRole('heading', { name: text(locale, 'upload.title') }),
        ).toBeVisible()
        await page.goto('/app/playbooks')
        await page.getByRole('button', { name: text(locale, 'admin.playbooks.new') }).click()
        await page.getByRole('button', { name: text(locale, 'admin.playbooks.save') }).click()
        await expectNoSeriousViolations(page, 'playbook form with errors')
      })

      test('NFR-08 compare and the review ticket', async ({ page }) => {
        await page.goto('/demo-user')
        await expect(
          page.getByRole('heading', { name: text(locale, 'upload.title') }),
        ).toBeVisible()
        await page.goto('/app/compare')
        await expectNoSeriousViolations(page, 'compare start')
        await page.getByRole('button', { name: text(locale, 'compare.start') }).click()
        await expect(page.getByTestId('compare-summary')).toBeVisible({ timeout: 90_000 })
        await expectNoSeriousViolations(page, 'compare result')
      })
    })
  }
}
