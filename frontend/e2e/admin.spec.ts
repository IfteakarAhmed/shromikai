import { expect, test, type Page } from '@playwright/test'
import { analyseSample, inLocale, LOCALES, text, toNumber } from './support'

/** A worker runs the recorded sample, which sends its uncertain findings to review; returns the result's ID. */
async function workerRunsSample(page: Page, locale: (typeof LOCALES)[number]): Promise<string> {
  await page.goto('/demo-user')
  await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
  await analyseSample(page, locale)
  const id = /\/app\/analyses\/([0-9a-f-]{36})/.exec(page.url())?.[1]
  expect(id, 'the result address holds the analysis ID').toBeDefined()
  return id ?? ''
}

function sections(page: Page, locale: (typeof LOCALES)[number]) {
  return page.getByRole('navigation', { name: text(locale, 'admin.nav.label') })
}

async function asAdmin(page: Page, locale: (typeof LOCALES)[number]) {
  await page.goto('/demo-admin')
  await expect(page.getByRole('heading', { name: text(locale, 'admin.title') })).toBeVisible()
}

for (const locale of LOCALES) {
  test.describe(`admin (${locale})`, () => {
    test.beforeEach(async ({ page }) => {
      await inLocale(page, locale)
    })

    test('FR-60 FR-73 admin review: take an item, a decision needs a note, then it is approved @rules-off', async ({
      page,
    }) => {
      await workerRunsSample(page, locale)
      await asAdmin(page, locale)

      await sections(page, locale)
        .getByRole('link', { name: text(locale, 'admin.nav.review') })
        .click()
      await expect(
        page.getByRole('heading', { name: text(locale, 'admin.review.title'), level: 2 }),
      ).toBeVisible()
      const open = page.getByRole('button', { name: new RegExp(text(locale, 'admin.review.open')) })
      const link = page.getByRole('link', { name: new RegExp(text(locale, 'admin.review.open')) })
      await ((await open.count()) > 0 ? open : link).first().click()

      const detail = page.getByRole('region', { name: /./ }).filter({
        has: page.getByRole('button', { name: text(locale, 'admin.review.claimButton') }),
      })
      await expect(detail).toBeVisible()
      // The summary shown is a paraphrase, never contract text.
      await expect(page.getByText(text(locale, 'admin.review.summary'))).toBeVisible()

      await page.getByRole('button', { name: text(locale, 'admin.review.claimButton') }).click()
      await expect(page.getByText(text(locale, 'admin.review.decide.claimed'))).toBeVisible()

      await page
        .getByRole('radio', { name: text(locale, 'admin.review.decide.APPROVED_WITHOUT_CITATION') })
        .check()
      await page.getByRole('button', { name: text(locale, 'admin.review.decide.submit') }).click()
      await expect(page.getByText(text(locale, 'admin.review.decide.noteRequired'))).toBeVisible()
      const note = page.getByLabel(text(locale, 'admin.review.decide.note'))
      await expect(note).toHaveAttribute('aria-invalid', 'true')

      await note.fill('Checked by hand against the section; the claim holds.')
      await page.getByRole('button', { name: text(locale, 'admin.review.decide.submit') }).click()
      await expect(page.getByText(text(locale, 'admin.review.decide.saved'))).toBeVisible()
      // Decided: the form is gone and the note is kept with the decision.
      await expect(page.getByRole('radio')).toHaveCount(0)
      await expect(
        page.getByText(
          text(locale, 'admin.review.decidedNote', {
            note: 'Checked by hand against the section; the claim holds.',
          }),
        ),
      ).toBeVisible()
    })

    test('FR-73 the overview and the usage page show KPI-12 to KPI-14 and the day table', async ({
      page,
    }) => {
      await workerRunsSample(page, locale)
      await asAdmin(page, locale)

      const tiles = page.getByTestId('kpi-value')
      await expect(tiles.first()).toBeVisible()
      // KPI-12: a percentage with its count beside it once an analysis has finished (R-07).
      await expect(tiles.first()).toHaveText(/%/)
      await expect(
        // The count may carry a thousands separator once the demo has run over 999 analyses.
        page.getByText(/^[\d,০-৯]+(টির মধ্যে|\s+of\s)/).first(),
      ).toBeVisible()

      await sections(page, locale)
        .getByRole('link', { name: text(locale, 'admin.nav.usage') })
        .click()
      const table = page.getByRole('table')
      await expect(table).toBeVisible()
      await expect(table.getByRole('row')).toHaveCount(8) // header + 7 days
      // The charts load as their own chunk and are a picture of the table.
      await expect(page.locator('.recharts-responsive-container').first()).toBeVisible({
        timeout: 20_000,
      })
      const analyses = await table.getByRole('row').last().getByRole('cell').first().textContent()
      expect(toNumber(analyses ?? '')).toBeGreaterThanOrEqual(1)
    })

    test('FR-65 an API key: the secret is shown once, then only its prefix; revoking works', async ({
      page,
    }) => {
      await asAdmin(page, locale)
      await sections(page, locale)
        .getByRole('link', { name: text(locale, 'admin.nav.keys') })
        .click()

      await page.getByLabel(text(locale, 'admin.keys.name')).fill('e2e key')
      await page.getByRole('button', { name: text(locale, 'admin.keys.submit') }).click()

      const secret = page.getByTestId('api-key-secret')
      await expect(secret).toBeVisible()
      const value = ((await secret.textContent()) ?? '').trim()
      expect(value).toMatch(/^shk_/)
      await page.getByRole('button', { name: text(locale, 'admin.keys.done') }).click()
      await expect(secret).toHaveCount(0)
      await expect(page.locator('body')).not.toContainText(value)
      await page.reload()
      await expect(page.locator('body')).not.toContainText(value)

      page.once('dialog', (dialog) => {
        void dialog.accept()
      })
      // Other runs may have left keys of the same name, so count the active ones before and after.
      const active = page
        .getByRole('row')
        .filter({ hasText: 'e2e key' })
        .filter({ hasText: text(locale, 'admin.keys.active') })
      await expect(page.getByRole('table')).toBeVisible()
      await expect(active.first()).toBeVisible()
      const before = await active.count()
      expect(before).toBeGreaterThanOrEqual(1)
      await active
        .first()
        .getByRole('button', { name: new RegExp(text(locale, 'admin.keys.revoke')) })
        .click()
      await expect(active).toHaveCount(before - 1)
    })

    test('FR-73 FR-82 the trace viewer shows the steps of an analysis without contract text', async ({
      page,
    }) => {
      const id = await workerRunsSample(page, locale)
      await asAdmin(page, locale)
      await sections(page, locale)
        .getByRole('link', { name: text(locale, 'admin.nav.trace') })
        .click()

      await page.getByLabel(text(locale, 'admin.trace.idLabel')).fill(id)
      await page.getByRole('button', { name: text(locale, 'admin.trace.open') }).click()

      const table = page.getByRole('table')
      await expect(table).toBeVisible()
      expect(await table.getByRole('row').count()).toBeGreaterThan(4)
      await expect(table.getByText('RISK').first()).toBeVisible()
      // R-02: the trace never carries a long run of words from the contract.
      const shown = (await table.textContent()) ?? ''
      expect(shown).not.toContain('Probation')
    })

    test('FR-57 an HR user writes a playbook and archives it', async ({ page }) => {
      await page.goto('/demo-hr')
      await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
      await page.goto('/app/playbooks')
      await expect(
        page.getByRole('heading', {
          name: text(locale, 'admin.playbooks.title'),
          level: 2,
          exact: true,
        }),
      ).toBeVisible()

      await page.getByRole('button', { name: text(locale, 'admin.playbooks.new') }).click()
      await page
        .getByLabel(text(locale, 'admin.playbooks.name'), { exact: true })
        .fill('E2E policy')
      await page.getByLabel(text(locale, 'admin.playbooks.value')).fill('9')
      await page
        .getByLabel(text(locale, 'admin.playbooks.messageBn'))
        .fill('দৈনিক কাজ ৯ ঘণ্টার বেশি নয়')
      await page
        .getByLabel(text(locale, 'admin.playbooks.messageEn'))
        .fill('A day is at most 9 hours')
      await page.getByRole('button', { name: text(locale, 'admin.playbooks.save') }).click()

      await expect(page.getByText(text(locale, 'admin.playbooks.saved'))).toBeVisible()
      await expect(page.getByRole('heading', { name: 'E2E policy' }).first()).toBeVisible()

      page.once('dialog', (dialog) => {
        void dialog.accept()
      })
      await page
        .locator('li')
        .filter({ hasText: 'E2E policy' })
        .first()
        .getByRole('button', { name: new RegExp(text(locale, 'admin.playbooks.archive')) })
        .click()
      await expect(page.getByText(text(locale, 'admin.playbooks.archived'))).toBeVisible()
    })

    test('FR-06 a worker is refused the admin console', async ({ page }) => {
      await page.goto('/demo-user')
      await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
      await page.goto('/admin')
      await expect(page.getByRole('heading', { name: text(locale, 'admin.title') })).toHaveCount(0)
    })
  })
}
