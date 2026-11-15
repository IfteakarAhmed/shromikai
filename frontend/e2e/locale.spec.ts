import { expect, test, type Page } from '@playwright/test'
import { analyseSample, englishPhrases, text } from './support'

test.describe('first load', () => {
  test.use({ locale: 'en-US', extraHTTPHeaders: { 'Accept-Language': 'en-US,en;q=0.9' } })

  test('R-11 opens in Bangla although the browser asks for English', async ({ page }) => {
    await page.goto('/')

    await expect(page.locator('html')).toHaveAttribute('lang', 'bn')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(text('bn', 'hero.title'))
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: text('bn', 'login.title') })).toBeVisible()
  })

  test('R-11 only the visitor can switch to English, and it is remembered', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: /English/ }).click()
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(text('en', 'hero.title'))
    await page.reload()
    await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  })
})

async function noEnglishInterfaceText(page: Page, where: string) {
  const shown = await page.evaluate(() => document.body.innerText)
  const leaked = englishPhrases().filter((phrase) => shown.includes(phrase))
  expect(leaked, `English interface text on ${where} in Bangla`).toEqual([])
}

test('R-11 no English interface text is left on any core screen in Bangla', async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem('shromikai.locale', 'bn')
  })
  await page.goto('/')
  await noEnglishInterfaceText(page, '/')
  await page.goto('/login')
  await noEnglishInterfaceText(page, '/login')
  await page.goto('/demo-user')
  await expect(page.getByRole('heading', { name: text('bn', 'upload.title') })).toBeVisible()
  await noEnglishInterfaceText(page, '/app')
  await analyseSample(page, 'bn')
  await page.getByText(text('bn', 'results.method')).click()
  await noEnglishInterfaceText(page, 'results')
  await page
    .locator('article')
    .filter({ hasText: text('bn', 'verification.verified') })
    .first()
    .getByRole('button', { name: /খুলুন/ })
    .first()
    .click()
  await expect(page.getByRole('dialog').locator('mark.quote').first()).toBeVisible()
  await noEnglishInterfaceText(page, 'citation sheet')
})
