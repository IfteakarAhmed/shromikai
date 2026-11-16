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

/** Any digits in a string (a section number, a count) in Bangla numerals for bn (NFR-09). */
export function localizeDigits(value: string, language: Language): string {
  return language === 'bn'
    ? value.replace(/[0-9]/g, (digit) => BANGLA_DIGITS.charAt(Number(digit)))
    : value
}

/** A ratio such as 0.75 as a percent with its numerals, for example 75% or ৭৫%. */
export function formatPercent(ratio: number, language: Language): string {
  return new Intl.NumberFormat(language === 'bn' ? 'bn-BD' : 'en-US', {
    style: 'percent',
    maximumFractionDigits: 1,
  }).format(ratio)
}

/** A decimal with at most `digits` places in the reader's numerals. */
export function formatDecimal(value: number, language: Language, digits = 2): string {
  return new Intl.NumberFormat(language === 'bn' ? 'bn-BD' : 'en-US', {
    maximumFractionDigits: digits,
  }).format(value)
}

/** US dollars with the fraction a small model cost needs; Bangla says "ডলার" and keeps no Latin letters. */
export function formatUsd(value: number, language: Language): string {
  if (language === 'bn') {
    const amount = new Intl.NumberFormat('bn-BD', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 4,
    }).format(value)
    return `${amount} ডলার`
  }
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(value)
}

/** A date and time in Bangladesh time, for the audit-minded admin screens. */
export function formatDateTime(iso: string, language: Language): string {
  return new Intl.DateTimeFormat(language === 'bn' ? 'bn-BD' : 'en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Dhaka',
  }).format(new Date(iso))
}

/** A calendar day (YYYY-MM-DD) in the reader's language. */
export function formatDay(isoDate: string, language: Language): string {
  return new Intl.DateTimeFormat(language === 'bn' ? 'bn-BD' : 'en-GB', {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(new Date(`${isoDate}T00:00:00Z`))
}
