import { flushSync } from 'react-dom'
import type { MouseEvent } from 'react'

type TransitionDocument = Document & { startViewTransition?: (update: () => void) => unknown }

/**
 * Runs a route change inside a View Transition when the browser has them, motion is allowed and
 * low-bandwidth mode is off; otherwise just runs it (C1 route transitions, no dependency).
 */
export function withViewTransition(update: () => void, lowBandwidth: boolean): void {
  const doc = document as TransitionDocument
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (typeof doc.startViewTransition !== 'function' || reduced || lowBandwidth) {
    update()
    return
  }
  doc.startViewTransition(() => {
    flushSync(update)
  })
}

/** A plain left click on a same-tab link: the only click a route transition may take over. */
export function isPlainClick(event: MouseEvent<HTMLAnchorElement>, target?: string): boolean {
  return (
    !event.defaultPrevented &&
    event.button === 0 &&
    !event.metaKey &&
    !event.ctrlKey &&
    !event.shiftKey &&
    !event.altKey &&
    (target === undefined || target === '_self')
  )
}
