import type { KeyboardEvent, ReactNode } from 'react'
import { isStaticReplay } from '../../api/replay'
import { cx } from '../../lib/cx'
import { LinkButton } from './Button'
import { buttonClass, type ButtonSize, type ButtonVariant } from './buttonClass'
import { Icon } from './Icon'
import { LIVE_ONLY_DIMMED, focusLiveOnlyNote, useLiveOnlyText } from './liveOnlyProps'

/** The short inline note next to a control that needs a live server; nothing on a live build. */
export function LiveOnlyNote({ id, className }: { id: string; className?: string }) {
  const text = useLiveOnlyText()
  if (!isStaticReplay()) {
    return null
  }
  return (
    <p
      id={id}
      role="note"
      tabIndex={-1}
      className={cx(
        'mt-2 flex items-start gap-2 rounded-lg border border-rejected/40 bg-rejected/10 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-primary',
        className,
      )}
    >
      <Icon name="info" size={16} className="mt-0.5 shrink-0 text-rejected" />
      <span>{text}</span>
    </p>
  )
}

/**
 * A link that cannot be followed on the static replay: no destination, `aria-disabled`, focusable,
 * and a click or Enter moves focus to the visible note.
 */
export function LiveOnlyLink({
  noteId,
  className,
  children,
}: {
  noteId: string
  className?: string
  children: ReactNode
}) {
  const text = useLiveOnlyText()
  return (
    // An anchor with no href is not a link to the browser: the role and tab stop are given here.
    <a
      role="link"
      tabIndex={0}
      aria-disabled="true"
      aria-describedby={noteId}
      title={text}
      className={cx(className, LIVE_ONLY_DIMMED)}
      onClick={(event) => {
        event.preventDefault()
        focusLiveOnlyNote(noteId)
      }}
      onKeyDown={(event: KeyboardEvent) => {
        if (event.key === 'Enter') {
          event.preventDefault()
          focusLiveOnlyNote(noteId)
        }
      }}
    >
      {children}
    </a>
  )
}

interface LiveLinkButtonProps {
  to: string
  noteId: string
  variant?: ButtonVariant
  size?: ButtonSize
  className?: string
  /** False for a link that works on the replay too (the worker demo); default true. */
  needsServer?: boolean
  children: ReactNode
}

/** A `LinkButton` to a feature that needs a live server: disabled, with a note, on the replay. */
export function LiveLinkButton({
  to,
  noteId,
  variant = 'primary',
  size = 'md',
  className,
  needsServer = true,
  children,
}: LiveLinkButtonProps) {
  if (needsServer && isStaticReplay()) {
    return (
      <LiveOnlyLink noteId={noteId} className={buttonClass(variant, size, className)}>
        {children}
      </LiveOnlyLink>
    )
  }
  return (
    <LinkButton to={to} variant={variant} size={size} className={className}>
      {children}
    </LinkButton>
  )
}
