import { cx } from '../../lib/cx'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'outline' | 'danger'
export type ButtonSize = 'md' | 'lg' | 'icon'

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-primary-foreground hover:bg-primary/90',
  secondary: 'border border-border bg-secondary text-secondary-foreground hover:bg-accent',
  ghost: 'text-foreground hover:bg-accent',
  outline: 'border border-border bg-background/60 text-foreground hover:bg-accent',
  danger: 'bg-rejected text-background hover:bg-rejected/90',
}

const SIZES: Record<ButtonSize, string> = {
  md: 'min-h-11 px-4 text-base',
  lg: 'min-h-12 px-6 text-lg',
  icon: 'size-11 p-0',
}

/** Every button is at least 44 px tall (NFR-08). */
export function buttonClass(variant: ButtonVariant, size: ButtonSize, className?: string): string {
  return cx(
    'inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition-colors',
    'disabled:pointer-events-none disabled:opacity-55',
    VARIANTS[variant],
    SIZES[size],
    className,
  )
}
