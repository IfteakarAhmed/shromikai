import type { ReactNode } from 'react'
import { cx } from '../../lib/cx'
import { GlassCard } from './GlassCard'

/**
 * A data table on one glass card (C4). The card never leans (it is wide and holds links and
 * buttons); the table scrolls sideways inside its own keyboard-reachable region, so the glass
 * stays still behind it. Put a `<caption className="sr-only">` first, so the table has a name.
 */
export function TableShell({
  label,
  minWidth,
  className,
  children,
}: {
  label?: string
  minWidth?: string
  className?: string
  children: ReactNode
}) {
  return (
    <GlassCard depth="still" className={className}>
      <div
        role="region"
        aria-label={label}
        tabIndex={0}
        className="relative overflow-x-auto rounded-2xl"
      >
        <table
          className={cx('data-table')}
          style={minWidth === undefined ? undefined : { minWidth }}
        >
          {children}
        </table>
      </div>
    </GlassCard>
  )
}
