import { cx } from '../../lib/cx'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'outline' | 'danger'
export type ButtonSize = 'md' | 'lg' | 'icon'

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'btn-glow bg-primary text-primary-foreground hover:bg-primary/90',
  secondary: 'border border-border bg-secondary text-secondary-foreground hover:bg-accent',
  ghost: 'text-foreground hover:bg-accent',
  outline: 'border border-border bg-background/60 text-foreground hover:bg-accent',
  danger: 'bg-rejected text-background hover:bg-rejected/90',
}

// Phones get 56 px touch targets; from tablet width up 44 to 48 px (NFR-08, C1).
const SIZES: Record<ButtonSize, string> = {
  md: 'min-h-14 px-5 text-base md:min-h-11 md:px-4',
  lg: 'min-h-14 px-6 text-lg md:min-h-12',
  icon: 'size-14 p-0 md:size-11',
}

export function buttonClass(variant: ButtonVariant, size: ButtonSize, className?: string): string {
  return cx(
    'btn inline-flex shrink-0 items-center justify-center gap-2 rounded-xl font-semibold',
    'disabled:pointer-events-none disabled:opacity-55',
    VARIANTS[variant],
    SIZES[size],
    className,
  )
}
