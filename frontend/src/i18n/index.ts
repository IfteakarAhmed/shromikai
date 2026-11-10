import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import bn from './bn.json'
import en from './en.json'

export const LANGUAGES = ['bn', 'en'] as const
export type Language = (typeof LANGUAGES)[number]

/** UI preference only (AGENTS.md §6: the locale is one of the few values allowed in storage). */
const STORAGE_KEY = 'shromikai.locale'

export function storedLanguage(): Language {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY)
    return value === 'en' ? 'en' : 'bn'
  } catch {
    return 'bn'
  }
}

export function rememberLanguage(language: Language): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // Storage may be unavailable (private mode); the choice then lasts for this page only.
  }
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
