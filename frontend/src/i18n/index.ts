import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { readPreference, writePreference } from '../lib/storage'
import bn from './bn.json'
import en from './en.json'

export const LANGUAGES = ['bn', 'en'] as const
export type Language = (typeof LANGUAGES)[number]

/** UI preference only (AGENTS.md §6: the locale is one of the few values allowed in storage). */
export function storedLanguage(): Language {
  return readPreference('locale')
}

export function rememberLanguage(language: Language): void {
  writePreference('locale', language)
}

void i18n.use(initReactI18next).init({
  resources: { bn: { translation: bn }, en: { translation: en } },
  lng: storedLanguage(),
  fallbackLng: 'bn',
  supportedLngs: LANGUAGES,
  interpolation: { escapeValue: false },
  returnNull: false,
})

export default i18n
