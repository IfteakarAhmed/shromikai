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
