import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { readPreference, writePreference, type Locale, type Side, type Theme } from '../lib/storage'
import { PreferencesContext, type PreferencesApi } from './preferencesContext'

/**
 * The four UI preferences that may be remembered on the device (AGENTS.md §6): language, side,
 * low-bandwidth mode and theme. They are mirrored on the root element so the CSS and assistive
 * technology see them (`lang`, `data-theme`, `data-lowbw`).
 */
export function PreferencesProvider({ children }: { children: ReactNode }) {
  const { i18n } = useTranslation()
  const locale: Locale = i18n.language === 'en' ? 'en' : 'bn'
  const [side, setSideState] = useState<Side>(() => readPreference('side'))
  const [lowBandwidth, setLowBandwidthState] = useState<boolean>(() =>
    readPreference('lowBandwidth'),
  )
  const [theme, setThemeState] = useState<Theme>(() => readPreference('theme'))

  useEffect(() => {
    document.documentElement.lang = locale
  }, [locale])
  useEffect(() => {
    document.documentElement.dataset['theme'] = theme
  }, [theme])
  useEffect(() => {
    document.documentElement.dataset['lowbw'] = String(lowBandwidth)
  }, [lowBandwidth])

  const setLocale = useCallback(
    (next: Locale) => {
      writePreference('locale', next)
      void i18n.changeLanguage(next)
    },
    [i18n],
  )
  const setSide = useCallback((next: Side) => {
    writePreference('side', next)
    setSideState(next)
  }, [])
  const setLowBandwidth = useCallback((on: boolean) => {
    writePreference('lowBandwidth', on)
    setLowBandwidthState(on)
  }, [])
  const setTheme = useCallback((next: Theme) => {
    writePreference('theme', next)
    document.documentElement.dataset['theme'] = next
    setThemeState(next)
  }, [])

  const value = useMemo<PreferencesApi>(
    () => ({ locale, setLocale, side, setSide, lowBandwidth, setLowBandwidth, theme, setTheme }),
    [locale, setLocale, side, setSide, lowBandwidth, setLowBandwidth, theme, setTheme],
  )
  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>
}
