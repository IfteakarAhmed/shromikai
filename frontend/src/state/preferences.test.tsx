import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ALLOWED_STORAGE_KEYS } from '../lib/storage'
import { PreferencesProvider } from './PreferencesProvider'
import { usePreferences } from './preferencesContext'

function Probe() {
  const p = usePreferences()
  return (
    <div>
      <p data-testid="state">
        {p.locale}|{p.side}|{String(p.lowBandwidth)}|{p.theme}
      </p>
      <button
        type="button"
        onClick={() => {
          p.setLocale('en')
          p.setSide('EMPLOYER')
          p.setLowBandwidth(true)
          p.setTheme('light')
        }}
      >
        change
      </button>
    </div>
  )
}

describe('PreferencesProvider', () => {
  it('FR-75 starts in Bangla, worker side, normal bandwidth and the dark theme', () => {
    render(
      <PreferencesProvider>
        <Probe />
      </PreferencesProvider>,
    )

    expect(screen.getByTestId('state')).toHaveTextContent('bn|WORKER|false|dark')
    expect(document.documentElement.lang).toBe('bn')
    expect(document.documentElement.dataset['theme']).toBe('dark')
    expect(document.documentElement.dataset['lowbw']).toBe('false')
  })

  it('FR-75 FR-76 changes show on the root element and persist only allowlisted keys', async () => {
    render(
      <PreferencesProvider>
        <Probe />
      </PreferencesProvider>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'change' }))

    expect(screen.getByTestId('state')).toHaveTextContent('en|EMPLOYER|true|light')
    expect(document.documentElement.lang).toBe('en')
    expect(document.documentElement.dataset['theme']).toBe('light')
    expect(document.documentElement.dataset['lowbw']).toBe('true')
    for (const key of Object.keys(window.localStorage)) {
      expect(ALLOWED_STORAGE_KEYS).toContain(key)
    }
  })
})
