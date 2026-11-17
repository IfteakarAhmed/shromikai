import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { inLocale, LOCALES, text, type Locale } from './support'

/**
 * Increment C4: the admin console, the docs hub, the sign-in pages and the 403 and 404 pages in the
 * one visual language: themed tables, forms and charts, depth on read-only cards (a card that holds
 * a control never leans), architecture pictures that read in both themes and on a phone, the
 * profile, ticket and compare pages, and Back and Forward inside a View Transition.
 *
 * Pictures: P9_SHOTS=C4 npx playwright test e2e/c4.spec.ts -g pictures
 */
const THEMES = ['dark', 'light'] as const
type Theme = (typeof THEMES)[number]

async function inTheme(page: Page, theme: Theme) {
  await page.addInitScript((value) => {
    window.localStorage.setItem('shromikai.theme', value)
  }, theme)
}

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

async function expectNoSidewaysScroll(page: Page, where: string) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
  expect(overflow, `sideways scroll on ${where}`).toBeLessThanOrEqual(1)
}

async function asRole(page: Page, role: 'user' | 'admin', locale: Locale) {
  await page.goto(`/demo-${role}`)
  await expect(
    page.getByRole('heading', {
      name: text(locale, role === 'admin' ? 'admin.title' : 'upload.title'),
    }),
  ).toBeVisible()
}

/** Every card that holds a control, and every card that leans, on the page. */
async function cardCensus(page: Page) {
  return page.evaluate(() => {
    const controls = 'a[href], button, summary, details, input, select, textarea'
    const cards = Array.from(document.querySelectorAll<HTMLElement>('.depth-card'))
    return {
      total: cards.length,
      withControls: cards.filter((card) => card.querySelector(controls) !== null).length,
      leaningWithControls: cards.filter(
        (card) => card.querySelector(controls) !== null && card.dataset['tilt'] === 'on',
      ).length,
      leaning: cards.filter((card) => card.dataset['tilt'] === 'on').length,
    }
  })
}

for (const locale of LOCALES) {
  for (const theme of THEMES) {
    test.describe(`C4 (${locale}, ${theme})`, () => {
      test.beforeEach(async ({ page }) => {
        await inLocale(page, locale)
        await inTheme(page, theme)
      })

      test('FR-68 NFR-08 the 404 page: a card with the code, the reason and three ways out', async ({
        page,
      }) => {
        await page.goto('/this/page/is/not/here')
        const main = page.getByRole('main')
        await expect(
          main.getByRole('heading', { level: 1, name: text(locale, 'notFound.title') }),
        ).toBeVisible()
        await expect(main.getByText(text(locale, 'notFound.body'))).toBeVisible()
        await expect(main.locator('.status-code')).toHaveAttribute('aria-hidden', 'true')
        for (const [name, href] of [
          [text(locale, 'common.home'), '/'],
          [text(locale, 'notFound.analyse'), '/app'],
          [text(locale, 'nav.docs'), '/docs'],
        ] as const) {
          await expect(main.getByRole('link', { name })).toHaveAttribute('href', href)
        }
        await expectNoSidewaysScroll(page, '404')
        await expectNoSeriousViolations(page, '404')
      })

      test('FR-68 NFR-08 the 403 page: a worker opening /admin is told, kindly, and can switch account', async ({
        page,
      }) => {
        await asRole(page, 'user', locale)
        await page.goto('/admin')
        const main = page.getByRole('main')
        await expect(
          main.getByRole('heading', { level: 1, name: text(locale, 'forbidden.title') }),
        ).toBeVisible()
        await expect(main.getByRole('alert')).toContainText(text(locale, 'forbidden.body'))
        await expect(
          main.getByRole('link', { name: text(locale, 'forbidden.switch') }),
        ).toHaveAttribute('href', '/login')
        await expectNoSidewaysScroll(page, '403')
        await expectNoSeriousViolations(page, '403')
      })

      test('FR-68 NFR-08 the profile and the ticket page', async ({ page }) => {
        await asRole(page, 'user', locale)
        await page.goto('/app/profile')
        await expect(
          page.getByRole('heading', { name: text(locale, 'profile.title'), level: 1 }),
        ).toBeVisible()
        await expect(page.getByTestId('profile-effect')).toBeVisible()
        await expectNoSidewaysScroll(page, 'profile')
        await expectNoSeriousViolations(page, 'profile')

        await page.goto('/app/tickets/77777777-7777-4777-8777-777777777777')
        await expect(page.getByLabel(text(locale, 'ticket.token'))).toBeVisible()
        await expectNoSidewaysScroll(page, 'ticket')
        await expectNoSeriousViolations(page, 'ticket')
      })

      test('FR-68 forms: one themed field with a 3:1 edge, a hover edge and a glowing focus', async ({
        page,
      }) => {
        await asRole(page, 'user', locale)
        await page.goto('/app/profile')
        const field = page.getByLabel(text(locale, 'profile.jobRole'))
        await expect(field).toHaveClass(/\bfield\b/)

        const edge = await page.evaluate(() => {
          const probe = document.createElement('i')
          probe.style.color = 'var(--input)'
          document.body.append(probe)
          const value = getComputedStyle(probe).color
          probe.remove()
          return value
        })
        const rest = await field.evaluate((el) => getComputedStyle(el).borderTopColor)
        expect(rest).toBe(edge)
        expect(await field.evaluate((el) => getComputedStyle(el).backgroundColor)).not.toBe(
          'rgba(0, 0, 0, 0)',
        )

        await field.hover()
        await expect
          .poll(() => field.evaluate((el) => getComputedStyle(el).borderTopColor))
          .not.toBe(edge)
        await page.mouse.move(0, 0)
        await field.focus()
        await page.keyboard.press('Tab')
        await field.focus()
        await expect
          .poll(() => field.evaluate((el) => getComputedStyle(el).boxShadow))
          .not.toBe('none')
      })

      test('FR-68 FR-73 tables: one themed header, a hover row, a keyboard-reachable scroll region', async ({
        page,
      }) => {
        await asRole(page, 'admin', locale)
        await page.goto('/admin/usage')
        const table = page.locator('table.data-table').first()
        await expect(table).toBeVisible()
        const header = table.locator('thead th').first()
        expect(await header.evaluate((el) => getComputedStyle(el).backgroundColor)).not.toBe(
          'rgba(0, 0, 0, 0)',
        )
        const region = page.getByRole('region', { name: text(locale, 'admin.usage.tableTitle') })
        await expect(region).toHaveAttribute('tabindex', '0')
        await expect(region.locator('..')).toHaveClass(/depth-card/)

        const row = table.locator('tbody tr').first()
        const before = await row.evaluate((el) => getComputedStyle(el).backgroundColor)
        await row.hover()
        await expect
          .poll(() => row.evaluate((el) => getComputedStyle(el).backgroundColor))
          .not.toBe(before)
      })

      test('FR-73 charts: bars, legend and axes take their colours from the theme', async ({
        page,
      }) => {
        await asRole(page, 'admin', locale)
        await page.goto('/admin/usage')
        await expect(page.locator('.recharts-wrapper').first()).toBeVisible({ timeout: 30_000 })

        const colours = await page.evaluate(() => {
          const body = getComputedStyle(document.body).color
          const legend = document.querySelector('.recharts-legend-item-text')
          const tick = document.querySelector('.recharts-cartesian-axis-tick-value')
          const bar = document.querySelector('.recharts-bar-rectangle path')
          return {
            body,
            legend:
              legend === null ? null : getComputedStyle(legend.firstElementChild ?? legend).color,
            tick: tick === null ? null : getComputedStyle(tick).fill,
            bar: bar === null ? null : getComputedStyle(bar).fill,
          }
        })
        expect(colours.legend).toBe(colours.body)
        expect(colours.tick).not.toBeNull()
        expect(colours.tick).not.toBe(colours.body) // muted, not the full text colour
        if (colours.bar !== null) {
          expect(colours.bar).not.toBe('rgb(0, 0, 0)')
        }
        await expectNoSeriousViolations(page, 'usage charts')
      })

      test('NFR-08 FR-73 a wide table scrolls inside its card and never widens the page', async ({
        page,
      }) => {
        await asRole(page, 'admin', locale)
        for (const route of [
          '/admin/api-keys',
          '/admin/usage',
          '/admin/traces',
          '/docs/data',
          '/docs/evaluation',
          '/docs/mcp',
          '/docs/prompts',
          '/docs/security',
          '/docs/architecture',
          '/docs/known-gaps',
        ]) {
          await page.goto(route)
          await page.waitForLoadState('networkidle')
          await page.waitForTimeout(800) // the Markdown sections of these pages load after the frame
          await expectNoSidewaysScroll(page, route)
        }
      })

      test('FR-74 NFR-08 architecture pictures: one drawing shown, text at 12 px or more, no sideways scroll', async ({
        page,
      }) => {
        await page.goto('/docs/architecture')
        await expect(page.locator('svg[data-variant]').first()).toBeAttached()
        const width = page.viewportSize()?.width ?? 0
        const shown = width >= 1024 ? 'wide' : 'narrow'
        const hidden = shown === 'wide' ? 'narrow' : 'wide'

        for (const variant of [shown, hidden]) {
          const visible = await page
            .locator(`svg[data-variant="${variant}"]`)
            .evaluateAll((svgs) =>
              svgs.map((svg) => (svg as SVGSVGElement).getBoundingClientRect().width > 0),
            )
          expect(visible, variant).toEqual([variant === shown, variant === shown])
        }

        const smallest = await page.locator(`svg[data-variant="${shown}"]`).evaluateAll((svgs) =>
          svgs.map((svg) => {
            const sizes = Array.from(svg.querySelectorAll<SVGTextElement>('text')).map((node) => {
              const scale = node.getScreenCTM()?.a ?? 1
              return Number(node.getAttribute('font-size')) * scale
            })
            return Math.min(...sizes)
          }),
        )
        for (const size of smallest) {
          expect(size).toBeGreaterThanOrEqual(11.9)
        }
        await expectNoSidewaysScroll(page, 'architecture')
        await expectNoSeriousViolations(page, 'architecture')
      })

      test('FR-68 depth: a card that holds a control never leans; read-only cards may', async ({
        page,
      }, info) => {
        await asRole(page, 'admin', locale)
        for (const route of ['/admin', '/admin/api-keys', '/docs', '/docs/architecture']) {
          await page.goto(route)
          await page.waitForLoadState('networkidle')
          const census = await cardCensus(page)
          expect(census.total, route).toBeGreaterThan(0)
          expect(census.leaningWithControls, `${route}: cards with controls that lean`).toBe(0)
          if (info.project.name === 'desktop-chromium' && route === '/admin') {
            expect(census.leaning, 'read-only tiles lean with a fine pointer').toBeGreaterThan(0)
          }
        }
      })

      test('FR-72 FR-68 compare start: two cards side by side on a desktop, stacked on a phone', async ({
        page,
      }) => {
        await asRole(page, 'user', locale)
        await page.goto('/app/compare')
        const grid = page.getByTestId('compare-start-grid')
        await expect(grid).toBeVisible()
        const cards = await grid.locator('> .depth-card').evaluateAll((nodes) =>
          nodes.map((node) => {
            const box = node.getBoundingClientRect()
            return { x: box.x, y: box.y }
          }),
        )
        expect(cards).toHaveLength(2)
        const sideBySide = Math.abs((cards[0]?.y ?? 0) - (cards[1]?.y ?? 0)) < 4
        expect(sideBySide).toBe((page.viewportSize()?.width ?? 0) >= 1024)
        await expectNoSidewaysScroll(page, 'compare start')
      })
    })
  }
}

test.describe('C4 Back and Forward', () => {
  test.beforeEach(async ({ page }) => {
    await inLocale(page, 'bn')
    // Count the View Transitions the page starts.
    await page.addInitScript(() => {
      const w = window as unknown as { __transitions: number }
      w.__transitions = 0
      const original = document.startViewTransition.bind(document)
      document.startViewTransition = ((update?: () => void) => {
        w.__transitions += 1
        return original(update)
      }) as typeof document.startViewTransition
    })
  })

  const count = (page: Page) =>
    page.evaluate(() => (window as unknown as { __transitions: number }).__transitions)

  test('FR-68 Back and Forward start a View Transition and the page still follows the address', async ({
    page,
  }) => {
    await page.goto('/login')
    await page
      .getByRole('link', { name: text('bn', 'footer.docs') })
      .first()
      .click()
    await expect(page).toHaveURL(/\/docs$/)
    const afterPush = await count(page)

    await page.goBack()
    await expect(page).toHaveURL(/\/login$/)
    await expect(
      page.getByRole('heading', { name: text('bn', 'login.title'), level: 1 }),
    ).toBeVisible()
    expect(await count(page)).toBeGreaterThan(afterPush)

    const afterBack = await count(page)
    await page.goForward()
    await expect(page).toHaveURL(/\/docs$/)
    await expect(page.getByRole('navigation', { name: text('bn', 'docs.navLabel') })).toBeVisible()
    expect(await count(page)).toBeGreaterThan(afterBack)
  })

  test('NFR-08 FR-76 with reduced motion or low bandwidth Back changes the page with no transition', async ({
    page,
  }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/login')
    await page
      .getByRole('link', { name: text('bn', 'footer.docs') })
      .first()
      .click()
    await expect(page).toHaveURL(/\/docs$/)
    const before = await count(page)
    await page.goBack()
    await expect(page).toHaveURL(/\/login$/)
    await expect(
      page.getByRole('heading', { name: text('bn', 'login.title'), level: 1 }),
    ).toBeVisible()
    expect(await count(page)).toBe(before)
  })
})

// --- pictures for the report (opt in) -------------------------------------------------------
const shots = process.env['P9_SHOTS'] === 'C4'
const folder = resolve(process.cwd(), '..', 'docs', 'img', 'p9')

test.describe('C4 pictures', () => {
  test.skip(!shots, 'set P9_SHOTS=C4 to take the report pictures')

  for (const theme of THEMES) {
    test(`pictures (bn, ${theme})`, async ({ page }) => {
      test.setTimeout(240_000)
      await inLocale(page, 'bn')
      await inTheme(page, theme)
      const width = page.viewportSize()?.width ?? 0
      mkdirSync(folder, { recursive: true })
      const shot = async (name: string, fullPage = false) => {
        await page.waitForTimeout(500)
        await page.screenshot({
          path: resolve(folder, `C4-${name}-${theme}-${String(width)}.png`),
          fullPage,
        })
      }

      await page.goto('/login')
      await shot('login')
      await page.goto('/no/such/page')
      await shot('404')

      await asRole(page, 'user', 'bn')
      await page.goto('/admin')
      await expect(page.getByRole('heading', { name: text('bn', 'forbidden.title') })).toBeVisible()
      await shot('403')
      await page.goto('/app/profile')
      await expect(page.getByTestId('profile-effect')).toBeVisible()
      await shot('profile')
      await page.goto('/app/tickets/77777777-7777-4777-8777-777777777777')
      await shot('ticket-code')
      await page.goto('/app/compare')
      await shot('compare-start')
      await page.getByRole('button', { name: text('bn', 'compare.start') }).click()
      await expect(page.getByTestId('compare-summary')).toBeVisible({ timeout: 90_000 })
      await page.waitForLoadState('networkidle')
      await shot('compare-result')
      await shot('compare-result-full', true)

      await asRole(page, 'admin', 'bn')
      for (const [route, name] of [
        ['/admin', 'admin-home'],
        ['/admin/review', 'admin-review'],
        ['/admin/playbooks', 'admin-playbooks'],
        ['/admin/api-keys', 'admin-api-keys'],
        ['/admin/usage', 'admin-usage'],
        ['/admin/traces', 'admin-trace'],
      ] as const) {
        await page.goto(route)
        await page.waitForLoadState('networkidle')
        await shot(name, name === 'admin-usage')
      }
      for (const [route, name] of [
        ['/docs', 'docs-index'],
        ['/docs/architecture', 'docs-architecture'],
        ['/docs/data', 'docs-data'],
        ['/docs/evaluation', 'docs-evaluation'],
        ['/docs/audit', 'docs-audit'],
        ['/docs/security', 'docs-security'],
      ] as const) {
        await page.goto(route)
        await page.waitForLoadState('networkidle')
        await shot(name, name === 'docs-architecture' || name === 'docs-data')
      }
    })
  }
})
