import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { analyseSample, inLocale, LOCALES, text } from './support'

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

      for (const route of ['/', '/login', '/docs']) {
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
      })
    })
  }
}
