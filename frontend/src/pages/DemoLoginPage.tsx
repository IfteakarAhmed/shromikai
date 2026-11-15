import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate } from 'react-router-dom'
import { LinkButton } from '../components/ui/Button'
import type { DemoRole } from '../api/auth'
import { useAuth } from '../auth/authContext'

/**
 * One-click demo access for judges (FR-08): /demo-user, /demo-hr and /demo-admin sign in as the
 * seeded account and land on /app, or /admin for the admin.
 */
export function DemoLoginPage({ role }: { role: DemoRole }) {
  const { t } = useTranslation()
  const { demoLogin, session } = useAuth()
  const [failed, setFailed] = useState(false)
  const started = useRef(false)

  useEffect(() => {
    if (started.current) {
      return
    }
    started.current = true
    demoLogin(role).catch(() => {
      setFailed(true)
    })
  }, [demoLogin, role])

  if (session.status === 'authenticated' && !failed) {
    return <Navigate to={role === 'admin' ? '/admin' : '/app'} replace />
  }
  if (failed) {
    return (
      <section className="mx-auto max-w-2xl px-(--gutter) py-12">
        <p role="alert" className="text-lg text-rejected">
          {t('demo.failed')}
        </p>
        <div className="mt-6">
          <LinkButton to="/login" variant="outline">
            {t('demo.toLogin')}
          </LinkButton>
        </div>
      </section>
    )
  }
  return (
    <p role="status" className="mx-auto max-w-2xl px-(--gutter) py-12 text-muted-foreground">
      {t('demo.signingIn')}
    </p>
  )
}
