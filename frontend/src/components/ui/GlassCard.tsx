import type { HTMLAttributes } from 'react'
import { cx } from '../../lib/cx'

interface GlassCardProps extends HTMLAttributes<HTMLElement> {
  as?: 'article' | 'aside' | 'div' | 'section' | 'li'
  interactive?: boolean
}

/** A translucent panel (flat in low-bandwidth mode, see index.css). */
export function GlassCard({
  as: Element = 'div',
  interactive = false,
  className,
  ...props
}: GlassCardProps) {
  return (
    <Element
      className={cx('glass-panel rounded-xl', interactive && 'glass-interactive', className)}
      {...props}
    />
  )
}
