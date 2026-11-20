import type { MouseEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { isStaticReplay } from '../../api/replay'
import { cx } from '../../lib/cx'

/** The class every control that needs a live server wears in the static replay: dimmed. */
export const LIVE_ONLY_DIMMED = 'opacity-55 cursor-not-allowed'

/** Moves focus to the visible note of a disabled control, so a click is never silent. */
export function focusLiveOnlyNote(noteId: string): void {
  document.getElementById(noteId)?.focus()
}

/**
 * What a control that needs a live server gets on the static recorded replay (FR-85, R-12):
 * `aria-disabled`, a dimmed look, a pointer to its visible note, and a click that moves focus to
 * that note instead of doing nothing. On a live build it is empty, so spreading it is free. The
 * element stays focusable (a natively disabled one is skipped by keyboard and screen readers).
 * `active` false leaves one control of a group (the recorded worker side) untouched.
 */
export function liveOnlyProps(
  noteId: string,
  className?: string,
  active = true,
): {
  'aria-disabled'?: true
  'aria-describedby'?: string
  className?: string
  onClick?: (event: MouseEvent<HTMLElement>) => void
} {
  if (!active || !isStaticReplay()) {
    return className === undefined ? {} : { className }
  }
  return {
    'aria-disabled': true,
    'aria-describedby': noteId,
    className: cx(className, LIVE_ONLY_DIMMED),
    onClick: (event) => {
      event.preventDefault()
      event.stopPropagation()
      focusLiveOnlyNote(noteId)
    },
  }
}

/** "Needs a live server; this site is a recorded replay. Use 'Try a sample contract'." */
export function useLiveOnlyText(): string {
  const { t } = useTranslation()
  return t('replay.liveOnly', { sample: t('upload.sample') })
}
