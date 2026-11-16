import type { ReactNode } from 'react'
import { Icon, type IconName } from './Icon'

interface EmptyStateProps {
  title: string
  description?: string
  icon?: IconName
  action?: ReactNode
}

export function EmptyState({ title, description, icon = 'file', action }: EmptyStateProps) {
  return (
    <div className="flex min-h-48 flex-col items-center justify-center gap-3 px-6 py-10 text-center">
      <span className="flex size-14 items-center justify-center rounded-full border border-glass-border bg-secondary text-primary shadow-[0_0_40px_-8px_var(--glow-primary)]">
        <Icon name={icon} size={22} />
      </span>
      <h2 className="text-lg font-semibold">{title}</h2>
      {description !== undefined && <p className="max-w-md text-muted-foreground">{description}</p>}
      {action}
    </div>
  )
}
