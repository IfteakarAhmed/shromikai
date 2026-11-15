import { useEffect, useId, useRef, type ReactNode, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import { Icon } from './Icon'

interface SheetProps {
  open: boolean
  onClose: () => void
  title: string
  description?: string
  children: ReactNode
}

/**
 * A side sheet on wide screens and a bottom sheet on phones, built on the native `<dialog>`: it
 * traps focus, closes on Esc, makes the page behind it inert and returns focus to the control that
 * opened it, with no dependency (ADR-0023).
 */
export function Sheet({ open, onClose, title, description, children }: SheetProps) {
  const { t } = useTranslation()
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (dialog === null) {
      return
    }
    if (open && !dialog.open) {
      dialog.showModal()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  function closeOnBackdrop(event: SyntheticEvent<HTMLDialogElement, MouseEvent>) {
    if (event.target === event.currentTarget) {
      onClose()
    }
  }

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onClose}
      onClick={closeOnBackdrop}
      className="sheet fixed inset-x-0 bottom-0 top-auto max-h-[88dvh] w-full overflow-hidden rounded-t-2xl shadow-glass md:inset-y-0 md:left-auto md:right-0 md:max-h-none md:h-dvh md:w-[34rem] md:rounded-none"
    >
      <div className="flex max-h-[88dvh] flex-col md:h-dvh md:max-h-none">
        <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <h2 id={titleId} className="text-xl font-bold">
              {title}
            </h2>
            {description !== undefined && (
              <p className="mt-1 text-muted-foreground">{description}</p>
            )}
          </div>
          <Button variant="ghost" size="icon" onClick={onClose} aria-label={t('common.close')}>
            <Icon name="x" />
          </Button>
        </header>
        <div
          tabIndex={0}
          role="region"
          aria-labelledby={titleId}
          className="overflow-y-auto px-5 py-4"
        >
          {children}
        </div>
      </div>
    </dialog>
  )
}
