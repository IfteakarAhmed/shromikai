import { useRef, type KeyboardEvent, type ReactNode, type TouchEvent } from 'react'
import { cx } from '../../lib/cx'

export interface TabItem {
  id: string
  label: ReactNode
  panel: ReactNode
}

interface TabsProps {
  items: readonly TabItem[]
  value: string
  onChange: (id: string) => void
  label: string
  idPrefix?: string
}

const SWIPE_DISTANCE = 56

/**
 * Accessible tabs (roving tabindex, arrow keys, Home and End) whose panels can also be swiped on a
 * touch screen. Only the open panel is rendered, so a long page does not carry the others in its tree.
 */
export function Tabs({ items, value, onChange, label, idPrefix = 'tabs' }: TabsProps) {
  const touchStart = useRef<{ x: number; y: number } | null>(null)
  const index = Math.max(
    0,
    items.findIndex((item) => item.id === value),
  )

  function move(delta: number) {
    const next = items[(index + delta + items.length) % items.length]
    if (next !== undefined) {
      onChange(next.id)
      document.getElementById(`${idPrefix}-tab-${next.id}`)?.focus()
    }
  }

  function onKeyDown(event: KeyboardEvent) {
    switch (event.key) {
      case 'ArrowRight':
        event.preventDefault()
        move(1)
        break
      case 'ArrowLeft':
        event.preventDefault()
        move(-1)
        break
      case 'Home':
        event.preventDefault()
        move(-index)
        break
      case 'End':
        event.preventDefault()
        move(items.length - 1 - index)
        break
    }
  }

  function onTouchStart(event: TouchEvent) {
    const touch = event.touches[0]
    touchStart.current = touch === undefined ? null : { x: touch.clientX, y: touch.clientY }
  }

  function onTouchEnd(event: TouchEvent) {
    const start = touchStart.current
    const touch = event.changedTouches[0]
    touchStart.current = null
    if (start === null || touch === undefined) {
      return
    }
    const dx = touch.clientX - start.x
    const dy = touch.clientY - start.y
    if (Math.abs(dx) >= SWIPE_DISTANCE && Math.abs(dx) > Math.abs(dy) * 1.5) {
      const next = items[index + (dx < 0 ? 1 : -1)]
      if (next !== undefined) {
        onChange(next.id)
      }
    }
  }

  return (
    <div>
      <div
        role="tablist"
        aria-label={label}
        onKeyDown={onKeyDown}
        className="flex flex-wrap gap-1 border-b border-border"
      >
        {items.map((item) => {
          const selected = item.id === value
          return (
            <button
              key={item.id}
              id={`${idPrefix}-tab-${item.id}`}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`${idPrefix}-panel-${item.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => {
                onChange(item.id)
              }}
              className={cx(
                'min-h-14 shrink-0 whitespace-nowrap border-b-2 px-4 font-semibold transition-colors md:min-h-11',
                selected
                  ? 'border-primary text-foreground shadow-[0_10px_24px_-18px_var(--glow-primary)]'
                  : 'border-transparent text-muted-foreground hover:text-foreground',
              )}
            >
              {item.label}
            </button>
          )
        })}
      </div>
      <div onTouchStart={onTouchStart} onTouchEnd={onTouchEnd} className="pt-5">
        {items.map((item) => (
          <div
            key={item.id}
            id={`${idPrefix}-panel-${item.id}`}
            role="tabpanel"
            aria-labelledby={`${idPrefix}-tab-${item.id}`}
            hidden={item.id !== value}
            tabIndex={0}
          >
            {item.id === value ? item.panel : null}
          </div>
        ))}
      </div>
    </div>
  )
}
