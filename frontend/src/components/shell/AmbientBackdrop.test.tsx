import { render } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { writePreference } from '../../lib/storage'
import { PreferencesProvider } from '../../state/PreferencesProvider'
import { AmbientBackdrop } from './AmbientBackdrop'

function backdrop() {
  return render(
    <PreferencesProvider>
      <AmbientBackdrop />
    </PreferencesProvider>,
  )
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('AmbientBackdrop', () => {
  it('NFR-08 draws three soft glow orbs as decoration hidden from assistive technology', () => {
    const { container } = backdrop()
    const layer = container.querySelector('[data-backdrop]')
    expect(layer).toHaveAttribute('aria-hidden', 'true')
    expect(layer?.querySelectorAll('[data-orb]')).toHaveLength(3)
    expect(layer).toHaveAttribute('data-motion', 'float')
  })

  it('NFR-08 keeps the orbs still when the visitor prefers reduced motion', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(
      (query: string) =>
        ({
          matches: query.includes('prefers-reduced-motion'),
          media: query,
          addEventListener: () => undefined,
          removeEventListener: () => undefined,
        }) as unknown as MediaQueryList,
    )
    const { container } = backdrop()
    expect(container.querySelector('[data-backdrop]')).toHaveAttribute('data-motion', 'still')
  })

  it('FR-76 draws nothing in low-bandwidth mode', () => {
    writePreference('lowBandwidth', true)
    const { container } = backdrop()
    expect(container.querySelector('[data-backdrop]')).toBeNull()
  })
})
