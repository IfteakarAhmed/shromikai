import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { makeResult } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { ResultsView } from './ResultsView'

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
  it('FR-85 hides both compare buttons entirely and keeps the new-analysis one', () => {
    view()
    expect(screen.queryByText(/সাধারণ এআইয়ের সাথে তুলনা/)).toBeNull()
    expect(screen.queryByRole('link', { name: 'তুলনা' })).toBeNull()
    expect(document.querySelector('a[href="/app/compare"]')).toBeNull()
    expect(screen.queryByText(/লাইভ সার্ভার লাগে/)).toBeNull()
    expect(screen.getAllByRole('link', { name: 'নতুন চুক্তি দিন' })).toHaveLength(2)
  })
})
