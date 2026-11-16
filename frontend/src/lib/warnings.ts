import type { TFunction } from 'i18next'

/** A warning of the analysis in the reader's language; a code the app does not know shows the API's own text. */
export function warningText(t: TFunction, warning: { code: string; message: string }): string {
  return t(`warningCodes.${warning.code}`, { defaultValue: warning.message })
}
