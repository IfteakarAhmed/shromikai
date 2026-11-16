import type { HTMLAttributes } from 'react'
import { cx } from '../../lib/cx'

export type BadgeTone =
  'neutral' | 'verified' | 'uncertain' | 'rejected' | 'critical' | 'high' | 'medium' | 'low'

const TONES: Record<BadgeTone, string> = {
  neutral: 'border-border bg-secondary text-secondary-foreground',
  verified: 'border-verified/40 bg-verified/10 text-verified',
  uncertain: 'border-uncertain/40 bg-uncertain/10 text-uncertain',
  rejected: 'border-rejected/40 bg-rejected/10 text-rejected',
  critical: 'border-sev-critical/50 bg-sev-critical/15 text-sev-critical',
  high: 'border-sev-high/50 bg-sev-high/10 text-sev-high',
  medium: 'border-sev-medium/50 bg-sev-medium/10 text-sev-medium',
  low: 'border-border bg-secondary text-sev-low',
}

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone
}

export function Badge({ tone = 'neutral', className, ...props }: BadgeProps) {
  return (
    <span
      className={cx(
        'font-ui inline-flex min-h-7 items-center gap-1.5 rounded-full border px-3 py-0.5 text-sm font-semibold leading-tight',
        TONES[tone],
        className,
      )}
      {...props}
    />
  )
}
