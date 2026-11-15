import { expect, test } from '@playwright/test'
import { analyseSample, inLocale, LOCALES, text } from './support'

async function hasNoHorizontalScroll(page: import('@playwright/test').Page) {
  const overflow = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    width: window.innerWidth,
  }))
  expect(overflow.scroll, 'the page is wider than the screen').toBeLessThanOrEqual(overflow.width)
}

for (const locale of LOCALES) {
  test.describe(`layout (${locale})`, () => {
    test.beforeEach(async ({ page }) => {
      await inLocale(page, locale)
    })

    for (const route of ['/', '/login', '/docs']) {
      test(`NFR-08 ${route} has no horizontal scroll`, async ({ page }) => {
        await page.goto(route)
        await page.waitForLoadState('networkidle')
        await hasNoHorizontalScroll(page)
      })
    }

    test('NFR-08 the upload and result screens have no horizontal scroll and reachable actions', async ({
      page,
    }, info) => {
      await page.goto('/demo-user')
      await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
      await hasNoHorizontalScroll(page)

      await analyseSample(page, locale)
      await hasNoHorizontalScroll(page)

      const mobile = (info.project.use.viewport?.width ?? 1280) < 768
      const newAnalysis = page.getByRole('link', { name: text(locale, 'results.newAnalysis') })
      if (mobile) {
        // On a phone the main actions stay in a bar at the bottom of the screen.
        const bar = newAnalysis.last()
        await expect(bar).toBeInViewport()
      }
      // Every interactive control is at least 44 px (NFR-08).
      const small = await page.evaluate(() =>
        Array.from(
          document.querySelectorAll('button, a[href], [role=tab], summary, input, textarea'),
        )
          .filter((el) => {
            const box = el.getBoundingClientRect()
            const style = getComputedStyle(el)
            return (
              box.width > 0 &&
              box.height > 0 &&
              style.visibility !== 'hidden' &&
              !el.closest('.sr-only') &&
              !el.classList.contains('sr-only') &&
              (box.height < 43.5 || box.width < 43.5)
            )
          })
          .map(
            (el) =>
              `${el.tagName} ${el.textContent.trim().slice(0, 30)} ${Math.round(el.getBoundingClientRect().width)}x${Math.round(el.getBoundingClientRect().height)}`,
          ),
      )
      expect(small, 'targets smaller than 44 px').toEqual([])
    })
  })
}
