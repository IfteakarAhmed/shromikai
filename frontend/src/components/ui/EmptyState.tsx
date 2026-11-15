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
      <span className="flex size-12 items-center justify-center rounded-full border border-border bg-secondary text-muted-foreground">
        <Icon name={icon} size={22} />
      </span>
      <h2 className="text-lg font-semibold">{title}</h2>
      {description !== undefined && <p className="max-w-md text-muted-foreground">{description}</p>}
      {action}
    </div>
  )
}
