import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import i18n from '../../i18n'
import { sampleMeta } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'

beforeEach(async () => {
  await i18n.changeLanguage('bn')
  vi.stubEnv('VITE_STATIC_REPLAY', '1')
  server.use(
    http.get('/replay/meta.bn.json', () => HttpResponse.json(sampleMeta)),
    http.get('/replay/login.worker.json', () =>
      HttpResponse.json({
        accessToken: 'stub',
        expiresIn: 900,
        guest: false,
        tokenType: 'Bearer',
        user: { id: 'u', email: 'w@example.test', role: 'WORKER' },
      }),
    ),
  )
})
afterEach(() => {
  vi.unstubAllEnvs()
})

/** The header's own links and buttons (desktop row and mobile menu), by name. */
const header = () => document.querySelector('header') as HTMLElement

describe('FR-85 header in the static recorded replay', () => {
  it('FR-85 hides Compare, Profile, Sign in and Sign out entirely, not dimmed', async () => {
    renderApp(<App />, '/app')
    // The recorded session is open, so Profile and Sign out would exist on a live build.
    await screen.findByRole('heading', { name: /চুক্তি/, level: 1 })
    const text = header().textContent
    for (const word of ['তুলনা', 'প্রোফাইল', 'সাইন ইন', 'সাইন আউট']) {
      expect(text).not.toContain(word)
    }
    expect(header().querySelector('[aria-disabled]')).toBeNull()
    expect(header().querySelector('a[href="/login"], a[href="/app/compare"]')).toBeNull()
  })

  it('FR-85 hides sign-in for a visitor with no recorded session too', async () => {
    renderApp(<App />, '/docs')
    await screen.findAllByRole('link', { name: 'ডকুমেন্টেশন' })
    expect(header().textContent).not.toContain('সাইন ইন')
    expect(header().querySelector('[aria-disabled]')).toBeNull()
  })

  it('FR-85 does not announce a signed-in account that nobody can sign out of', async () => {
    renderApp(<App />, '/app')
    await screen.findAllByRole('link', { name: 'ডকুমেন্টেশন' })
    expect(header().textContent).not.toContain('হিসেবে ঢুকেছেন')
    expect(header().textContent).not.toContain('w@example.test')
  })

  it('FR-85 keeps Analyse, Documentation, language, theme and low-bandwidth', async () => {
    renderApp(<App />, '/docs')
    expect((await screen.findAllByRole('link', { name: 'ডকুমেন্টেশন' }))[0]).toHaveAttribute(
      'href',
      '/docs',
    )
    expect(screen.getAllByRole('link', { name: 'বিশ্লেষণ' })[0]).toHaveAttribute('href', '/app')
    expect(within(header()).getAllByRole('switch').length).toBeGreaterThan(0)
    expect(within(header()).getAllByRole('button').length).toBeGreaterThan(2)
  })
})
