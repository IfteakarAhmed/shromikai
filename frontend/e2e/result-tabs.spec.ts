import { expect, test } from '@playwright/test'
import { inLocale, LOCALES, openResult, text } from './support'

for (const locale of LOCALES) {
  test.describe(`result tabs (${locale})`, () => {
    test.beforeEach(async ({ page }) => {
      await inLocale(page, locale)
      await openResult(page, locale)
    })

    test('FR-70 the why drawer shows the three checks of a verified risk and the reasons of an uncertain one', async ({
      page,
    }) => {
      const verified = page
        .locator('article')
        .filter({ hasText: text(locale, 'verification.verified') })
        .first()
      await verified.getByRole('button', { name: text(locale, 'finding.why') }).click()
      let dialog = page.getByRole('dialog')
      await expect(dialog.getByText(text(locale, 'why.checksTitle'))).toBeVisible()
      // A risk can rest on more than one section, and each section has its own three checks.
      await expect(dialog.getByText(text(locale, 'why.checkExistence')).first()).toBeVisible()
      await expect(dialog.getByText(text(locale, 'why.outcome.PASS')).first()).toBeVisible()
      await expect(dialog.getByText(/claude|recorded|scripted/i).first()).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(dialog).toBeHidden()

      const uncertain = page
        .locator('article')
        .filter({ hasText: text(locale, 'verification.sentForReview') })
        .first()
      await uncertain.getByRole('button', { name: text(locale, 'finding.why') }).click()
      dialog = page.getByRole('dialog')
      await expect(dialog.getByText(text(locale, 'why.uncertainTitle'))).toBeVisible()
      // The sections that were tried and why each one failed are listed.
      await expect(dialog.locator('code').first()).toBeVisible()
      await expect(dialog.getByRole('list').getByRole('listitem').first()).toBeVisible()
    })

    test('FR-71 the redline marks removed and added words; duties can be ticked; nothing is stored', async ({
      page,
    }) => {
      await page.getByRole('tab', { name: text(locale, 'tabs.proposals') }).click()
      await expect(page.locator('del').first()).toBeVisible()
      await expect(page.locator('ins').first()).toBeVisible()

      await page.getByRole('tab', { name: text(locale, 'tabs.duties') }).click()
      const boxes = page.getByRole('checkbox')
      await expect(boxes.first()).toBeVisible()
      await boxes.first().check()
      await expect(boxes.first()).toBeChecked()
      const stored = await page.evaluate(() => JSON.stringify(window.localStorage))
      expect(stored).not.toMatch(/obligation|o1|duty/i)
      // The ticks survive a change of tab (they live in the page), not a reload.
      await page.getByRole('tab', { name: text(locale, 'tabs.risks') }).click()
      await page.getByRole('tab', { name: text(locale, 'tabs.duties') }).click()
      await expect(page.getByRole('checkbox').first()).toBeChecked()
    })

    test('FR-55 FR-71 the memo previews, downloads as a PDF from the server and prints on its own', async ({
      page,
    }) => {
      await page.getByRole('tab', { name: text(locale, 'tabs.memo') }).click()
      await expect(page.locator('.memo-print')).toBeVisible()

      const download = page.waitForEvent('download', { timeout: 60_000 })
      await page.getByRole('button', { name: text(locale, 'memo.download') }).click()
      const file = await download
      expect(file.suggestedFilename()).toMatch(/^memo-.*\.pdf$/)
      const path = await file.path()
      const { readFileSync } = await import('node:fs')
      const bytes = readFileSync(path)
      expect(bytes.subarray(0, 4).toString()).toBe('%PDF')
      expect(bytes.length).toBeGreaterThan(5_000)

      // The print layout shows the memo and nothing else.
      await page.emulateMedia({ media: 'print' })
      await expect(page.locator('.memo-print')).toBeVisible()
      await expect(page.getByRole('banner')).toBeHidden()
      await expect(page.getByRole('tablist')).toBeHidden()
    })

    test('FR-61 an uncertain risk links to the status of its human review, with the code already known', async ({
      page,
    }) => {
      await page
        .getByRole('link', { name: text(locale, 'finding.ticketLink') })
        .first()
        .click()

      await expect(page.getByRole('heading', { name: text(locale, 'ticket.title') })).toBeVisible()
      await expect(page.getByText(text(locale, 'ticket.status.OPEN')).first()).toBeVisible()
    })
  })
}
