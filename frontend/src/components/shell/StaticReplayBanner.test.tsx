import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import i18n from '../../i18n'
import { Layout } from '../../pages/Layout'
import { sampleMeta } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
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

const BN =
  'একটি নমুনা চুক্তির রেকর্ড করা রিপ্লে, লাইভ সার্ভার নয়। আপলোড ও অ্যাকাউন্টের জন্য সম্পূর্ণ সংস্করণ লাগবে।'
const EN =
  'Recorded replay of one sample contract, not a live server. Upload and accounts need the full version.'

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('FR-85 static replay banner (R-12)', () => {
  it.each(['/', '/login', '/app', '/docs', '/docs/evaluation'])(
    'FR-85 shows the one concise banner on %s in Bangla',
    async (path) => {
      vi.stubEnv('VITE_STATIC_REPLAY', '1')
      at(path)
      await screen.findByText('page')
      expect(screen.getByRole('note', { name: 'রেকর্ড করা রিপ্লে' })).toHaveTextContent(BN)
    },
  )

  it('FR-85 shows the banner in English', async () => {
    vi.stubEnv('VITE_STATIC_REPLAY', '1')
    await i18n.changeLanguage('en')
    at('/app')
    await screen.findByText('page')
    expect(screen.getByRole('note', { name: 'Recorded replay' })).toHaveTextContent(EN)
  })

  it('FR-85 merges the recorded-demo banner into it: one banner, not two', async () => {
    vi.stubEnv('VITE_STATIC_REPLAY', '1')
    await i18n.changeLanguage('en')
    server.use(
      http.get('/replay/meta.en.json', () =>
        HttpResponse.json({
          ...sampleMeta,
          recordedDemo: { side: 'WORKER', outputLanguage: 'bn' },
        }),
      ),
    )
    at('/')
    await screen.findByText('page')
    // Let the meta query settle: the second banner would appear once it resolves.
    await new Promise((resolve) => setTimeout(resolve, 100))
    // The banners sit between the header and the page; the disclaimer is in the footer.
    expect(document.querySelectorAll('header ~ [role=note]')).toHaveLength(1)
    expect(screen.queryByText(/Sample data \(recorded\)/)).toBeNull()
  })

  it('FR-85 shows no banner on a live build', async () => {
    at('/app')
    await screen.findByText('page')
    expect(screen.queryByRole('note', { name: /Recorded replay|রেকর্ড করা রিপ্লে/ })).toBeNull()
    expect(screen.queryByText(/Recorded replay of one|রেকর্ড করা রিপ্লে/)).toBeNull()
  })
})
