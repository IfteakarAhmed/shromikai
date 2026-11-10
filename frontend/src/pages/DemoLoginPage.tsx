import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate } from 'react-router-dom'
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
      <section>
        <p role="alert" className="text-lg text-red-800">
          {t('demo.failed')}
        </p>
        <p className="mt-6">
          <Link to="/login" className="font-medium underline underline-offset-4">
            {t('demo.toLogin')}
          </Link>
        </p>
      </section>
    )
  }
  return (
    <p role="status" className="text-stone-700">
      {t('demo.signingIn')}
    </p>
  )
}
