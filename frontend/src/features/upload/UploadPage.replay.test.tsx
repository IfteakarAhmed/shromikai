import { createEvent, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { sampleMeta } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { UploadPage } from './UploadPage'

const NOTE_BN =
  "লাইভ সার্ভার লাগে; এই সাইটটি রেকর্ড করা রিপ্লে। 'নমুনা চুক্তি দিয়ে দেখুন' ব্যবহার করুন।"
const NOTE_EN = "Needs a live server; this site is a recorded replay. Use 'Try a sample contract'."

let posts = 0

beforeEach(async () => {
  await i18n.changeLanguage('bn')
  posts = 0
  vi.stubEnv('VITE_STATIC_REPLAY', '1')
  server.use(
    http.get('/replay/meta.bn.json', () =>
      HttpResponse.json({ ...sampleMeta, recordedDemo: { side: 'WORKER', outputLanguage: 'bn' } }),
    ),
    http.get('/replay/meta.en.json', () =>
      HttpResponse.json({ ...sampleMeta, recordedDemo: { side: 'WORKER', outputLanguage: 'en' } }),
    ),
    http.get('/replay/login.worker.json', () =>
      HttpResponse.json({
        accessToken: 'stub',
        expiresIn: 900,
        guest: false,
        tokenType: 'Bearer',
        user: { id: 'u', email: 'w@example.test', role: 'WORKER' },
      }),
    ),
    http.post('/api/v1/analyses', () => {
      posts += 1
      return new HttpResponse(null, { status: 500 })
    }),
  )
})
afterEach(() => {
  vi.unstubAllEnvs()
})

function page() {
  return renderApp(
    <Routes>
      <Route path="/app" element={<UploadPage />} />
    </Routes>,
    '/app',
  )
}

describe('FR-85 upload page in the static recorded replay', () => {
  it('FR-85 says that no document can be uploaded', async () => {
    page()
    expect(
      await screen.findByText(
        'স্ট্যাটিক রিপ্লেতে কোনো নথি আপলোড বা বিশ্লেষণ করা যায় না; শুধু রেকর্ড করা নমুনাটি দেখা যায়।',
      ),
    ).toBeInTheDocument()
  })

  it('FR-85 makes the sample the one primary action, before every disabled control', async () => {
    page()
    const sample = await screen.findByRole('button', { name: 'নমুনা চুক্তি দিয়ে দেখুন' })
    expect(sample).toBeEnabled()
    expect(sample).not.toHaveAttribute('aria-disabled')
    expect(sample).toHaveClass('btn-glow')
    expect(screen.getAllByRole('button', { name: 'নমুনা চুক্তি দিয়ে দেখুন' })).toHaveLength(1)
    const analyse = screen.getByRole('button', { name: 'বিশ্লেষণ করুন' })
    expect(sample.compareDocumentPosition(analyse) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(analyse).not.toHaveClass('btn-glow')
  })

  it('FR-85 shows Analyse, the file choice and the employer side as aria-disabled with a note', async () => {
    page()
    const analyse = await screen.findByRole('button', { name: 'বিশ্লেষণ করুন' })
    const choose = screen.getByRole('button', { name: 'ফাইল বেছে নিন' })
    const employer = screen.getByRole('radio', { name: /নিয়োগকর্তা/ })
    for (const control of [analyse, choose, employer]) {
      expect(control).toHaveAttribute('aria-disabled', 'true')
      expect(control).toHaveClass('opacity-55')
      const note = document.getElementById(control.getAttribute('aria-describedby') ?? '')
      expect(note).not.toBeNull()
      expect(note).toHaveTextContent(NOTE_BN)
    }
  })

  it('FR-85 shows the note in English', async () => {
    await i18n.changeLanguage('en')
    page()
    const analyse = await screen.findByRole('button', { name: 'Analyse' })
    const note = document.getElementById(analyse.getAttribute('aria-describedby') ?? '')
    expect(note).toHaveTextContent(NOTE_EN)
  })

  it('FR-85 a click on Analyse starts nothing and moves focus to the note, never silent', async () => {
    const user = userEvent.setup()
    page()
    const analyse = await screen.findByRole('button', { name: 'বিশ্লেষণ করুন' })
    await user.click(analyse)
    const note = document.getElementById(analyse.getAttribute('aria-describedby') ?? '')
    expect(note).toHaveFocus()
    expect(posts).toBe(0)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('FR-85 a file dropped on the drop zone is refused with focus on the note, not opened by the browser', async () => {
    page()
    const title = await screen.findByText('পিডিএফ ফাইলটি এখানে ছাড়ুন')
    const zone = title.closest('div') as HTMLElement
    const drop = createEvent.drop(zone)
    fireEvent(zone, drop)
    expect(drop.defaultPrevented).toBe(true)
    const choose = screen.getByRole('button', { name: 'ফাইল বেছে নিন' })
    expect(document.getElementById(choose.getAttribute('aria-describedby') ?? '')).toHaveFocus()
    expect(posts).toBe(0)
    // The words of the zone are dimmed like the controls.
    expect(title).toHaveClass('opacity-55')
  })

  it('FR-85 the photo and text methods are disabled too, each with its note', async () => {
    const user = userEvent.setup()
    page()
    await user.click(await screen.findByRole('tab', { name: 'ছবি বা ক্যামেরা' }))
    const photos = screen.getByRole('button', { name: 'গ্যালারি থেকে বেছে নিন' })
    expect(photos).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByRole('button', { name: 'ক্যামেরায় ছবি তুলুন' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )
    await user.click(screen.getByRole('tab', { name: 'লেখা পেস্ট' }))
    const text = screen.getByRole('textbox')
    expect(text).toHaveAttribute('aria-disabled', 'true')
    expect(text).toHaveAttribute('readonly')
    await user.type(text, 'abc')
    expect(text).toHaveValue('')
    await waitFor(() => {
      expect(
        document.getElementById(text.getAttribute('aria-describedby') ?? ''),
      ).toHaveTextContent(NOTE_BN)
    })
  })

  it('FR-85 the recorded worker side stays selected and shows no note of its own', async () => {
    page()
    const worker = await screen.findByRole('radio', { name: /কর্মী/ })
    expect(worker).toBeChecked()
    expect(worker).not.toHaveAttribute('aria-disabled')
    expect(
      within(screen.getByRole('group', { name: 'আপনি কোন পক্ষ?' })).queryAllByRole('note'),
    ).toHaveLength(1)
  })
})
