import type { Language } from '../i18n'

/** Bangla numerals for bn (NFR-09), Latin digits for en. */
export function formatNumber(value: number, language: Language): string {
  return new Intl.NumberFormat(language === 'bn' ? 'bn-BD' : 'en-US').format(value)
}

const BANGLA_DIGITS = '০১২৩৪৫৬৭৮৯'

/** Build version such as 0.1.0: Bangla digits for bn (NFR-09), unchanged for en. */
export function formatVersion(version: string, language: Language): string {
  return language === 'bn'
    ? version.replace(/[0-9]/g, (digit) => BANGLA_DIGITS.charAt(Number(digit)))
    : version
}
