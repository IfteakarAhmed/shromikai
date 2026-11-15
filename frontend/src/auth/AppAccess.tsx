import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { ErrorState } from '../components/ui/ErrorState'
import { useAuth } from './authContext'

/**
 * The worker area is open to everyone: a visitor without a session gets a guest session bound to
 * this browser's device token on first use (FR-09, architecture §17). Anyone signed in keeps their
 * own session.
 */
export function AppAccess({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const { session, guestLogin } = useAuth()
  const [error, setError] = useState<unknown>(null)
  const started = useRef(false)

  function start() {
    started.current = true
    setError(null)
    guestLogin().catch((failure: unknown) => {
      setError(failure)
    })
  }

  useEffect(() => {
    if (session.status === 'anonymous' && !started.current) {
      start()
    }
    if (session.status === 'authenticated' || session.status === 'guest') {
      started.current = false
    }
    // start() only reads refs and stable setters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.status])

  if (session.status === 'authenticated' || session.status === 'guest') {
    return <>{children}</>
  }
  if (error !== null) {
    return (
      <div className="mx-auto max-w-2xl px-(--gutter) py-10">
        <ErrorState error={error} onRetry={start} />
      </div>
    )
  }
  return (
    <p role="status" className="mx-auto max-w-2xl px-(--gutter) py-10 text-muted-foreground">
      {t('common.loading')}
    </p>
  )
}
