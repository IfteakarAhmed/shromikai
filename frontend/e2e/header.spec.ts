import { expect, test, type Page } from '@playwright/test'
import { inLocale, LOCALES, text, type Locale } from './support'

/**
 * The header at every width (C2a, the navbar bug from the D1 review): no two controls overlap, no
 * control's text wraps ("Sign out" stays on one line), no horizontal scroll; theme and language
 * are separate controls. Checked signed out, as a worker and as an admin (the longest menu), with
 * the phone menu closed and open.
 */
const WIDTHS = [390, 768, 1024, 1280] as const

async function problems(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const controls = Array.from(
      document.querySelectorAll<HTMLElement>('header a, header button'),
    ).filter((element) => {
      const box = element.getBoundingClientRect()
      return element.offsetParent !== null && box.width > 0 && box.height > 0
    })
    const found: string[] = []
    const name = (element: HTMLElement) =>
      (element.getAttribute('aria-label') ?? element.innerText).trim().replace(/\s+/g, ' ')
    for (const element of controls) {
      // A wrap is one text run broken over two lines (two pieces of different type faces
      // side by side may sit a few pixels apart, which is not a wrap).
      let wrapped = false
      const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
      for (let node = walker.nextNode(); node !== null; node = walker.nextNode()) {
        if (node.parentElement?.closest('.sr-only') || (node.textContent ?? '').trim() === '') {
          continue
        }
        const range = document.createRange()
        range.selectNodeContents(node)
        const rects = Array.from(range.getClientRects()).filter((rect) => rect.width > 2)
        const first = rects.at(0)
        if (first !== undefined && rects.some((rect) => rect.top >= first.bottom - 2)) {
          wrapped = true
        }
      }
      if (wrapped) {
        found.push(`wraps: ${name(element)}`)
      }
      if (element.scrollWidth > element.clientWidth + 1) {
        found.push(`overflows: ${name(element)}`)
      }
    }
    for (let i = 0; i < controls.length; i += 1) {
      for (let j = i + 1; j < controls.length; j += 1) {
        const a = controls[i]
        const b = controls[j]
        if (a.contains(b) || b.contains(a)) {
          continue
        }
        const p = a.getBoundingClientRect()
        const q = b.getBoundingClientRect()
        if (
          p.left < q.right - 1 &&
          q.left < p.right - 1 &&
          p.top < q.bottom - 1 &&
          q.top < p.bottom - 1
        ) {
          found.push(`overlap: ${name(a)} × ${name(b)}`)
        }
      }
    }
    if (document.documentElement.scrollWidth > window.innerWidth) {
      found.push(`horizontal scroll: ${String(document.documentElement.scrollWidth)}`)
    }
    return found
  })
}

async function checkHeader(page: Page, where: string, locale: Locale) {
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => document.fonts.ready)
  expect(await problems(page), `${where} menu closed`).toEqual([])
  const menu = page.getByRole('button', { name: text(locale, 'nav.menu') })
  if (await menu.isVisible()) {
    await menu.click()
    expect(await problems(page), `${where} menu open`).toEqual([])
    await expect(
      page.getByRole('button', { name: text(locale, 'theme.switchToLight') }),
    ).toBeVisible()
    await menu.click()
  } else {
    await expect(
      page.getByRole('button', { name: text(locale, 'theme.switchToLight') }),
    ).toBeVisible()
  }
}

test.describe('header layout', () => {
  test.skip(({ browserName }) => browserName !== 'chromium')

  for (const locale of LOCALES) {
    test(`NFR-08 no overlap or wrapping at 390, 768, 1024 and 1280 (${locale})`, async ({
      page,
    }, info) => {
      test.skip(info.project.name !== 'desktop-chromium', 'the widths are set by the test')
      test.setTimeout(180_000)
      await inLocale(page, locale)
      for (const width of WIDTHS) {
        await page.setViewportSize({ width, height: 800 })
        await page.goto('/login')
        await checkHeader(page, `${String(width)} signed out`, locale)
      }
      await page.goto('/demo-admin')
      await page.waitForURL(/\/admin/)
      for (const width of WIDTHS) {
        await page.setViewportSize({ width, height: 800 })
        await page.goto('/admin')
        await checkHeader(page, `${String(width)} admin`, locale)
      }
      await page.goto('/demo-user')
      await page.waitForURL(/\/app/)
      for (const width of WIDTHS) {
        await page.setViewportSize({ width, height: 800 })
        await page.goto('/app')
        await checkHeader(page, `${String(width)} worker`, locale)
      }
    })
  }
})
