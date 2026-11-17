import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page } from '@playwright/test'
import { inLocale, openDetails, openResult, text } from './support'

/**
 * The CSS 3D depth system (C2b, C3): read-only cards float, lean towards a fine pointer and lift
 * their marks in front of the glass; cards that hold controls never lean (usability first) but
 * keep the glass, the light edge and the pointer glow; orbs and headers drift on scroll. On touch,
 * with reduced motion and in low-bandwidth mode nothing leans.
 *
 * Pictures: P9_SHOTS=C2b npx playwright test e2e/depth.spec.ts
 */
const shots = process.env['P9_SHOTS'] === 'C2b'
const folder = resolve(process.cwd(), '..', 'docs', 'img', 'p9')

async function inTheme(page: Page, theme: 'dark' | 'light') {
  await page.addInitScript((value) => {
    window.localStorage.setItem('shromikai.theme', value)
  }, theme)
}

async function shot(page: Page, name: string, theme: string) {
  if (!shots) {
    return
  }
  mkdirSync(folder, { recursive: true })
  const width = page.viewportSize()?.width ?? 0
  await page.screenshot({
    path: resolve(folder, `C2b-${name}-${theme}-${String(width)}.png`),
  })
}

async function transformOf(target: Locator): Promise<string> {
  return target.first().evaluate((element) => getComputedStyle(element).transform)
}

/** Waits until the card's transform is the same in two reads 200 ms apart. */
async function settled(target: Locator) {
  await expect
    .poll(
      async () => {
        const before = await transformOf(target)
        await target.page().waitForTimeout(200)
        return before === (await transformOf(target))
      },
      { timeout: 5_000 },
    )
    .toBe(true)
}

/** Moves the pointer onto a card, ending near its right edge. */
async function hover(target: Locator): Promise<Locator> {
  const card = target.first()
  await card.scrollIntoViewIfNeeded()
  const box = await card.boundingBox()
  if (box === null) {
    throw new Error('no card')
  }
  const page = card.page()
  await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.2)
  await page.mouse.move(box.x + box.width * 0.85, box.y + box.height * 0.3, { steps: 6 })
  return card
}

/** A read-only card on the result: the stat tile with the total. */
const statTile = (page: Page) => page.locator('.depth-card:has([data-testid="total-findings"])')
/** A card that holds controls: the first risk card. */
const riskCard = (page: Page) => page.locator('article.depth-card').first()

/** The plain AI's first claim on the compare page: read-only, with lifted marks and text. */
async function baselineCard(page: Page): Promise<Locator> {
  await page.goto('/app/compare')
  await page.getByRole('button', { name: text('bn', 'compare.start') }).click()
  await expect(page.getByTestId('compare-summary')).toBeVisible({ timeout: 90_000 })
  return page
    .getByRole('region', { name: text('bn', 'compare.baselineTitle') })
    .locator('.depth-card')
    .first()
}

test.describe('depth system', () => {
  test.beforeEach(async ({ page }) => {
    await inLocale(page, 'bn')
  })

  test('FR-68 a read-only card leans towards a fine pointer, lifts its parts and settles flat', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await page.goto('/demo-user')
    await expect(page.getByRole('heading', { name: text('bn', 'upload.title') })).toBeVisible()
    const card = await hover(await baselineCard(page))
    await expect(card).toHaveAttribute('data-tilt', 'on')
    await expect.poll(() => transformOf(card), { timeout: 5_000 }).toMatch(/^matrix3d/)
    await expect(card).toHaveAttribute('data-tilting', 'true')
    // The marks and the text float in front of the glass.
    const lift = await card
      .locator('.lift-2, .lift-1')
      .first()
      .evaluate((element) => getComputedStyle(element).translate)
    expect(lift).toMatch(/px$/)
    // The glass layer is behind everything and does not take the pointer.
    const glass = await card.locator('> .depth-glass').evaluate((element) => {
      const style = getComputedStyle(element)
      return { events: style.pointerEvents, blur: style.backdropFilter }
    })
    expect(glass.events).toBe('none')
    expect(glass.blur).toMatch(/blur/)
    await page.mouse.move(2, 2)
    await expect.poll(() => transformOf(card), { timeout: 5_000 }).toBe('none')
  })

  test('FR-70 a risk card with controls never leans; its glow follows and its why button works', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await openResult(page, 'bn')
    const card = await hover(riskCard(page))
    await expect(card).toHaveAttribute('data-glowing', 'true')
    await expect(card).not.toHaveAttribute('data-tilt', 'on')
    expect(await transformOf(card)).toBe('none')
    await openDetails(card, 'bn')
    await card.getByRole('button', { name: text('bn', 'finding.why') }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
  })

  test('FR-71 a duty card with a checkbox never leans and its checkbox takes the click', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await openResult(page, 'bn')
    await page.goto(`${new URL(page.url()).pathname}?tab=duties`)
    const card = await hover(page.locator('[role="tabpanel"] .depth-card'))
    await expect(card).not.toHaveAttribute('data-tilt', 'on')
    expect(await transformOf(card)).toBe('none')
    const checkbox = card.getByRole('checkbox')
    await checkbox.click()
    await expect(checkbox).toBeChecked()
  })

  test('NFR-08 a read-only stat tile on the result leans and settles', async ({ page }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await openResult(page, 'bn')
    const tile = await hover(statTile(page))
    await expect.poll(() => transformOf(tile)).toMatch(/^matrix3d/)
    await settled(tile)
  })

  test('NFR-08 cards stay flat when the visitor prefers reduced motion', async ({ page }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await openResult(page, 'bn')
    const tile = await hover(statTile(page))
    await page.waitForTimeout(400)
    expect(await transformOf(tile)).toBe('none')
    await expect(tile).not.toHaveAttribute('data-tilt', 'on')
    const lift = await riskCard(page)
      .locator('.lift-2')
      .first()
      .evaluate((element) => getComputedStyle(element).translate)
    expect(lift).toBe('none')
    // The glass is still there: the same look, flat.
    await expect(tile.locator('> .depth-glass')).toBeAttached()
  })

  test('FR-76 cards stay flat in low-bandwidth mode', async ({ page }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await page.addInitScript(() => {
      window.localStorage.setItem('shromikai.lowbw', 'true')
    })
    await openResult(page, 'bn')
    const tile = await hover(statTile(page))
    await page.waitForTimeout(400)
    expect(await transformOf(tile)).toBe('none')
    await expect(tile).not.toHaveAttribute('data-tilt', 'on')
  })

  test('FR-76 cards stay flat on a touch phone and nothing drifts on scroll', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'mobile-chromium', 'touch only')
    await openResult(page, 'bn')
    const tile = statTile(page)
    await expect(tile).not.toHaveAttribute('data-tilt', 'on')
    expect(await transformOf(tile)).toBe('none')
    const drift = await page
      .locator('[data-parallax="1"]')
      .first()
      .evaluate((element) => getComputedStyle(element).animationName)
    expect(drift).toBe('none')
    const header = await page
      .locator('.parallax')
      .first()
      .evaluate((element) => getComputedStyle(element).animationName)
    expect(header).toBe('none')
  })

  test('FR-68 the orbs and page headers drift on scroll with a fine pointer', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
    await openResult(page, 'bn')
    const names = await page.evaluate(() => ({
      orb: getComputedStyle(document.querySelector('[data-parallax="1"]') as Element).animationName,
      header: getComputedStyle(document.querySelector('.parallax') as Element).animationName,
    }))
    expect(names.orb).toBe('orb-drift-up')
    expect(names.header).toBe('parallax-drift')
    // Reduced motion removes it.
    await page.emulateMedia({ reducedMotion: 'reduce' })
    const reduced = await page.evaluate(() => ({
      orb: getComputedStyle(document.querySelector('[data-parallax="1"]') as Element).animationName,
      header: getComputedStyle(document.querySelector('.parallax') as Element).animationName,
    }))
    expect(reduced).toEqual({ orb: 'none', header: 'none' })
  })

  test('FR-71 the printed memo is a plain document: no glass, no glow, no tilt', async ({
    page,
  }, info) => {
    test.skip(info.project.name !== 'desktop-chromium', 'one width is enough')
    await openResult(page, 'bn')
    await page.goto(`${new URL(page.url()).pathname}?tab=memo`)
    const memo = page.locator('.memo-print')
    await expect(memo).toBeVisible()
    await page.emulateMedia({ media: 'print' })
    const printed = await memo.evaluate((element) => ({
      glass: getComputedStyle(element.querySelector('.depth-glass') as Element).display,
      glow: getComputedStyle(element.querySelector('.depth-glow') as Element).display,
      transform: getComputedStyle(element).transform,
    }))
    expect(printed).toEqual({ glass: 'none', glow: 'none', transform: 'none' })
  })

  for (const theme of ['dark', 'light'] as const) {
    test(`NFR-08 axe, with a card leaning, on the result and the memo (${theme})`, async ({
      page,
    }, info) => {
      test.skip(info.project.name !== 'desktop-chromium', 'fine pointer only')
      await inTheme(page, theme)
      await openResult(page, 'bn')
      await hover(statTile(page))
      await page.waitForTimeout(400)
      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze()
      const serious = results.violations.filter(
        (violation) => violation.impact === 'serious' || violation.impact === 'critical',
      )
      expect(
        serious.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`),
        `axe on the leaning result (${theme})`,
      ).toEqual([])
    })
  }

  for (const theme of ['dark', 'light'] as const) {
    test(`report pictures of the depth system (${theme})`, async ({ page }) => {
      test.skip(!shots, 'set P9_SHOTS=C2b to take the report pictures')
      test.setTimeout(300_000)
      await inTheme(page, theme)
      await page.goto('/login')
      await page.waitForLoadState('networkidle')
      await shot(page, 'login', theme)
      await openResult(page, 'bn')
      await page.waitForTimeout(600)
      await shot(page, 'results-top', theme)
      await hover(riskCard(page)).catch(() => undefined)
      await page.waitForTimeout(500)
      await shot(page, 'results-card-hover', theme)
      await page.mouse.move(2, 2)
      await page.goto(`${new URL(page.url()).pathname}?tab=memo`)
      await expect(page.locator('.memo-print')).toBeVisible()
      await page.locator('.memo-print').scrollIntoViewIfNeeded()
      await page.waitForTimeout(500)
      await shot(page, 'memo', theme)
      await page.goto('/app/compare')
      await page.getByRole('button', { name: text('bn', 'compare.start') }).click()
      await expect(page.getByTestId('compare-summary')).toBeVisible({ timeout: 90_000 })
      await page.waitForTimeout(800)
      await shot(page, 'compare', theme)
      await page.goto('/?scene=lite')
      await page.waitForLoadState('networkidle')
      await page.locator('#audience-title').scrollIntoViewIfNeeded()
      await page.waitForTimeout(600)
      await shot(page, 'landing-cards', theme)
    })
  }
})
