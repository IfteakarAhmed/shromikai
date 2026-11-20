import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { renderApp } from '../../test/render'
import { LandingPage } from './LandingPage'

vi.mock('./cinematic/CinematicScene', () => ({ default: () => <div data-testid="cinematic" /> }))
vi.mock('./ContractScene', () => ({ default: () => <div data-testid="scene" /> }))

const NOTE_BN =
  "লাইভ সার্ভার লাগে; এই সাইটটি রেকর্ড করা রিপ্লে। 'নমুনা চুক্তি দিয়ে দেখুন' ব্যবহার করুন।"

beforeEach(async () => {
  await i18n.changeLanguage('bn')
  vi.stubEnv('VITE_STATIC_REPLAY', '1')
})
afterEach(() => {
  vi.unstubAllEnvs()
})

describe('FR-85 landing page in the static recorded replay', () => {
  it('FR-85 keeps the sample as the one primary action, with a real destination', () => {
    renderApp(<LandingPage />)
    const [sample] = screen.getAllByRole('link', { name: 'নমুনা চুক্তি দিয়ে দেখুন' })
    expect(sample).toHaveAttribute('href', '/app')
    expect(sample).not.toHaveAttribute('aria-disabled')
    expect(sample).toHaveClass('btn-glow')
  })

  it.each([
    ['নিজের চুক্তি দিন'],
    ['আমার চুক্তি দেব'],
    ['এইচআর হিসেবে ডেমো'],
    ['অ্যাডমিন হিসেবে ডেমো'],
  ])('FR-85 %s is aria-disabled with a visible note, not a link to nowhere', (name) => {
    renderApp(<LandingPage />)
    const controls = screen.queryAllByRole('link', { name: new RegExp(name) })
    expect(controls.length).toBeGreaterThan(0)
    for (const control of controls) {
      expect(control).toHaveAttribute('aria-disabled', 'true')
      expect(control).not.toHaveAttribute('href')
      const note = document.getElementById(control.getAttribute('aria-describedby') ?? '')
      expect(note).toHaveTextContent(NOTE_BN)
    }
  })

  it('FR-85 every link left on the page that goes to a live-only route is disabled', () => {
    renderApp(<LandingPage />)
    const liveOnly = ['/app/compare', '/demo-hr', '/demo-admin', '/login']
    const hrefs = screen.getAllByRole('link').map((link) => link.getAttribute('href'))
    for (const route of liveOnly) {
      expect(hrefs).not.toContain(route)
    }
  })

  it('FR-85 the compare button is hidden entirely, with no note of its own', () => {
    renderApp(<LandingPage />)
    expect(screen.queryByText('তুলনা দেখুন')).toBeNull()
    expect(document.querySelector('a[href="/app/compare"]')).toBeNull()
  })

  it('FR-85 the own-contract buttons stay visibly disabled with their note', () => {
    renderApp(<LandingPage />)
    const [own] = screen.getAllByRole('link', { name: 'নিজের চুক্তি দিন' })
    expect(own).toHaveAttribute('aria-disabled', 'true')
    expect(own).toHaveClass('opacity-55')
  })

  it('FR-85 a click on a disabled control moves focus to its note', async () => {
    const user = userEvent.setup()
    renderApp(<LandingPage />)
    const [own] = screen.getAllByRole('link', { name: 'নিজের চুক্তি দিন' })
    await user.click(own as HTMLElement)
    expect(document.getElementById(own?.getAttribute('aria-describedby') ?? '')).toHaveFocus()
  })
})
