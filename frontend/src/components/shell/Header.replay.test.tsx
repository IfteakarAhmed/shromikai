import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import i18n from '../../i18n'
import { sampleMeta } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'

const NOTE_BN =
  "লাইভ সার্ভার লাগে; এই সাইটটি রেকর্ড করা রিপ্লে। 'নমুনা চুক্তি দিয়ে দেখুন' ব্যবহার করুন।"

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

describe('FR-85 header in the static recorded replay', () => {
  it('FR-85 compare and profile are aria-disabled, dimmed and carry no destination', async () => {
    renderApp(<App />, '/app')
    const profile = await screen.findByRole('link', { name: 'প্রোফাইল' })
    for (const control of [profile, ...screen.getAllByRole('link', { name: 'তুলনা' })]) {
      expect(control).toHaveAttribute('aria-disabled', 'true')
      expect(control).not.toHaveAttribute('href')
      expect(control).toHaveClass('opacity-55')
      expect(control).toHaveAttribute('title', NOTE_BN)
    }
  })

  it('FR-85 the live links keep working: analyse and docs still have a destination', async () => {
    renderApp(<App />, '/app')
    await screen.findByRole('link', { name: 'প্রোফাইল' })
    expect(screen.getAllByRole('link', { name: 'ডকুমেন্টেশন' })[0]).toHaveAttribute('href', '/docs')
    expect(screen.getAllByRole('link', { name: 'বিশ্লেষণ' })[0]).toHaveAttribute('href', '/app')
  })

  it('FR-85 a click on a disabled link moves focus to the visible note, never silent', async () => {
    const user = userEvent.setup()
    renderApp(<App />, '/app')
    const [compare] = await screen.findAllByRole('link', { name: 'তুলনা' })
    await user.click(compare as HTMLElement)
    const note = document.getElementById(compare?.getAttribute('aria-describedby') ?? '')
    expect(note).toHaveFocus()
    expect(note).toHaveTextContent(NOTE_BN)
  })

  it('FR-85 sign-in is disabled for a visitor with no recorded session', async () => {
    renderApp(<App />, '/docs')
    const signIn = (await screen.findAllByRole('link', { name: 'সাইন ইন' }))[0]
    expect(signIn).toHaveAttribute('aria-disabled', 'true')
    expect(signIn).not.toHaveAttribute('href')
  })
})
