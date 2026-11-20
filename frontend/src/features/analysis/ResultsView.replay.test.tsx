import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { makeResult } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { ResultsView } from './ResultsView'

const NOTE_BN =
  "লাইভ সার্ভার লাগে; এই সাইটটি রেকর্ড করা রিপ্লে। 'নমুনা চুক্তি দিয়ে দেখুন' ব্যবহার করুন।"

function view() {
  return renderApp(
    <Routes>
      <Route path="/app/analyses/:id" element={<ResultsView result={makeResult()} />} />
    </Routes>,
    '/app/analyses/44444444-4444-4444-8444-444444444444',
  )
}

beforeEach(async () => {
  await i18n.changeLanguage('bn')
  vi.stubEnv('VITE_STATIC_REPLAY', '1')
})
afterEach(() => {
  vi.unstubAllEnvs()
})

describe('FR-85 results page in the static recorded replay', () => {
  it('FR-85 the compare buttons are aria-disabled with a note and no destination', () => {
    view()
    const controls = screen.getAllByRole('link', { name: /সাধারণ এআইয়ের সাথে তুলনা|^তুলনা$/ })
    expect(controls).toHaveLength(2)
    for (const control of controls) {
      expect(control).toHaveAttribute('aria-disabled', 'true')
      expect(control).not.toHaveAttribute('href')
      const note = document.getElementById(control.getAttribute('aria-describedby') ?? '')
      expect(note).toHaveTextContent(NOTE_BN)
    }
  })

  it('FR-85 a click on a disabled compare button moves focus to its note', async () => {
    const user = userEvent.setup()
    view()
    const [compare] = screen.getAllByRole('link', { name: /সাধারণ এআইয়ের সাথে তুলনা/ })
    await user.click(compare as HTMLElement)
    expect(document.getElementById(compare?.getAttribute('aria-describedby') ?? '')).toHaveFocus()
  })
})
