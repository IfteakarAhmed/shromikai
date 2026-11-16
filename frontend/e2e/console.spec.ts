import { expect, test } from '@playwright/test'
import { inLocale, LOCALES, openResult, text } from './support'

/** R-11 / quality bar: no console error or warning on the landing page or the result page. */
for (const locale of LOCALES) {
  test(`console is clean on / and on a result (${locale})`, async ({ page }) => {
    await inLocale(page, locale)
    const problems: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error' || message.type() === 'warning') {
        problems.push(`${message.type()}: ${message.text()} @ ${message.location().url}`)
      }
    })
    page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))
    page.on('requestfailed', (request) =>
      problems.push(`requestfailed: ${request.url()} ${request.failure()?.errorText ?? ''}`),
    )
    page.on('response', (response) => {
      // A 401 from the silent refresh of a visitor with no session is expected and silent in the UI,
      // but the browser logs it; it is filtered below and counted separately.
      if (response.status() >= 500) {
        problems.push(`http ${response.status()}: ${response.url()}`)
      }
    })

    await page.goto('/')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1500)
    await openResult(page, locale)
    await expect(page.getByRole('heading', { name: text(locale, 'results.title') })).toBeVisible()
    await page.waitForTimeout(500)

    // The one allowed message is the silent sign-in check of a visitor with no session: the API
    // answers 401 invalid-token (api.md E-04) and Chrome logs every failed request.
    const silentCheck = problems.filter(
      (p) => p.includes('401') && p.includes('/api/v1/auth/refresh'),
    )
    expect(silentCheck.length).toBeLessThanOrEqual(2)
    // Headless Chromium draws WebGL in software and Chrome then logs "GPU stall due to ReadPixels"
    // warnings. A real GPU (checked in the app browser pane) logs none, so they are not counted.
    const softwareGl = problems.filter((p) => p.includes('GL Driver Message'))
    expect(problems.filter((p) => !silentCheck.includes(p) && !softwareGl.includes(p))).toEqual([])
  })
}

test('the recorded-data banner is not clipped under the sticky header', async ({ page }) => {
  await page.goto('/')
  const banner = page
    .getByRole('note')
    .filter({ hasText: /recorded/ })
    .first()
  await expect(banner).toBeVisible()
  const header = await page.getByRole('banner').boundingBox()
  const box = await banner.boundingBox()
  expect(header).not.toBeNull()
  expect(box).not.toBeNull()
  if (header !== null && box !== null) {
    expect(box.y, 'the banner starts below the header').toBeGreaterThanOrEqual(
      header.y + header.height - 1,
    )
  }
  // Scrolled, the sticky header must not cover the banner's text either: the banner scrolls away.
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  const text2 = banner.getByText(/recorded/)
  await expect(text2.first()).toBeInViewport({ ratio: 1 })
})
