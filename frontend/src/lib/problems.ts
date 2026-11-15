import type { TFunction } from 'i18next'
import { ApiError, NetworkError } from '../api/client'
import { formatNumber } from './format'
import type { Language } from '../i18n'

/**
 * A message in the reader's language for anything the API (or the network) can throw. Every
 * problem code of api.md §7 has a message in bn and en under `problems.{code}`; a code the app does
 * not know yet falls back to the generic text and never shows a raw code to the user.
 */
export function describeError(error: unknown, t: TFunction, language: Language): string {
  if (error instanceof NetworkError) {
    return t('problems.network')
  }
  if (error instanceof ApiError) {
    const code = error.problem?.code
    if (code !== undefined) {
      const known = t(`problems.${code}`, { defaultValue: '' })
      if (known !== '') {
        const seconds = error.problem?.retryAfterSeconds
        if (typeof seconds === 'number' && seconds > 0) {
          return t('problems.retryIn', {
            message: known,
            minutes: formatNumber(Math.max(1, Math.ceil(seconds / 60)), language),
          })
        }
        return known
      }
    }
    return t('problems.unknown')
  }
  return t('problems.unknown')
}

/** The problem code behind an error, when the server sent one. */
export function problemCode(error: unknown): string | null {
  return error instanceof ApiError ? (error.problem?.code ?? null) : null
}
