import type { Language } from '../i18n'

/** Bangla numerals for bn (NFR-09), Latin digits for en. */
export function formatNumber(value: number, language: Language): string {
  return new Intl.NumberFormat(language === 'bn' ? 'bn-BD' : 'en-US').format(value)
}
