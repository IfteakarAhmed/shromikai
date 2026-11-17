import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate } from 'react-router-dom'
import { LinkButton } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { Icon } from '../components/ui/Icon'
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
      <DemoFrame>
        <p role="alert" className="mt-4 text-lg text-rejected">
          {t('demo.failed')}
        </p>
        <div className="mt-6">
          <LinkButton to="/login" variant="outline">
            {t('demo.toLogin')}
          </LinkButton>
        </div>
      </DemoFrame>
    )
  }
  return (
    <DemoFrame>
      <p
        role="status"
        className="mt-4 flex items-center justify-center gap-2 text-lg text-muted-foreground"
      >
        <Icon name="loader" className="animate-spin" />
        {t('demo.signingIn')}
      </p>
    </DemoFrame>
  )
}

/** The card both states of the one-click demo sign-in sit on. */
function DemoFrame({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation()
  return (
    <section className="mx-auto max-w-xl px-(--gutter) py-12">
      <GlassCard depth="still" className="p-8 text-center">
        <h1 className="text-3xl">{t('demo.title')}</h1>
        {children}
      </GlassCard>
    </section>
  )
}
