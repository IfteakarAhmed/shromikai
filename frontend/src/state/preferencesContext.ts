import { createContext, useContext } from 'react'
import type { Locale, Side, Theme } from '../lib/storage'

export interface PreferencesApi {
  locale: Locale
  setLocale: (locale: Locale) => void
  side: Side
  setSide: (side: Side) => void
  lowBandwidth: boolean
  setLowBandwidth: (on: boolean) => void
  theme: Theme
  setTheme: (theme: Theme) => void
}

export const PreferencesContext = createContext<PreferencesApi | null>(null)

export function usePreferences(): PreferencesApi {
  const value = useContext(PreferencesContext)
  if (value === null) {
    throw new Error('usePreferences must be used inside PreferencesProvider')
  }
  return value
}

/**
 * The preferences if a provider is above, or null. Presentational components (cards) use it so
 * they also work in isolation; the app always has the provider.
 */
export function usePreferencesOptional(): PreferencesApi | null {
  return useContext(PreferencesContext)
}
