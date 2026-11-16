import type { HTMLAttributes } from 'react'
import { cx } from '../../lib/cx'

interface GlassCardProps extends HTMLAttributes<HTMLElement> {
  as?: 'article' | 'aside' | 'div' | 'section' | 'li'
  interactive?: boolean
  /** Rises into place on scroll (transform only; off with reduced motion or low bandwidth). */
  reveal?: boolean
}

/** A glass panel with depth (flat in low-bandwidth mode, see index.css). */
export function GlassCard({
  as: Element = 'div',
  interactive = false,
  reveal = true,
  className,
  ...props
}: GlassCardProps) {
  return (
    <Element
      className={cx(
        'glass-panel rounded-2xl',
        interactive && 'glass-interactive',
        reveal && 'reveal',
        className,
      )}
      {...props}
    />
  )
}
