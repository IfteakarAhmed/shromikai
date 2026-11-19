import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import i18n from '../../i18n'
import { Layout } from '../../pages/Layout'
import { renderApp } from '../../test/render'

function at(path: string) {
  return renderApp(
    <Routes>
      <Route element={<Layout />}>
        <Route path="*" element={<p>page</p>} />
      </Route>
    </Routes>,
    path,
  )
}

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('FR-85 static replay label (R-12)', () => {
  it.each(['/', '/login', '/app', '/docs', '/docs/evaluation'])(
    'FR-85 shows the static-replay label on %s in Bangla',
    async (path) => {
      vi.stubEnv('VITE_STATIC_REPLAY', '1')
      at(path)
      await screen.findByText('page')
      expect(screen.getByRole('note', { name: /স্ট্যাটিক/ })).toHaveTextContent(
        'স্ট্যাটিক রেকর্ডকৃত রিপ্লে, লাইভ সার্ভার নয়',
      )
    },
  )

  it('FR-85 shows the label in English', async () => {
    vi.stubEnv('VITE_STATIC_REPLAY', '1')
    await i18n.changeLanguage('en')
    at('/app')
    await screen.findByText('page')
    expect(screen.getByRole('note', { name: /static/i })).toHaveTextContent(
      'Static recorded replay, not a live server',
    )
  })

  it('FR-85 shows no label on a live build', async () => {
    at('/app')
    await screen.findByText('page')
    expect(screen.queryByText(/Static recorded replay|স্ট্যাটিক রেকর্ডকৃত/)).toBeNull()
  })
})
