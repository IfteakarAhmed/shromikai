import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import i18n from '../../i18n'
import { PreferencesProvider } from '../../state/PreferencesProvider'
import { LowBandwidthToggle } from './LowBandwidthToggle'

describe('LowBandwidthToggle', () => {
  it('FR-76 is a compact switch that keeps its full name and state for everyone', () => {
    render(
      <PreferencesProvider>
        <LowBandwidthToggle />
      </PreferencesProvider>,
    )
    const toggle = screen.getByRole('switch', {
      name: new RegExp(i18n.t('header.lowBandwidth')),
    })
    expect(toggle).toHaveAttribute('aria-checked', 'false')
    expect(toggle).toHaveAttribute('title')
    expect(toggle).toHaveClass('whitespace-nowrap')
    // The words are for assistive technology and the tooltip; the button shows the icon and state.
    expect(screen.getByText(i18n.t('header.lowBandwidth'))).toHaveClass('sr-only')
  })
})
