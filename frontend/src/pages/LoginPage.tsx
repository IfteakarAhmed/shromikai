import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/authContext'
import { LoginForm } from '../auth/LoginForm'
import { Button, LinkButton } from '../components/ui/Button'
import { ErrorState } from '../components/ui/ErrorState'
import { GlassCard } from '../components/ui/GlassCard'
import { useTitle } from '../lib/useTitle'

/** Only same-site paths are accepted as a return address, never a full URL. */
function safeReturnPath(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/app'
}

export function LoginPage() {
  const { t } = useTranslation()
  useTitle(t('login.title'))
  const { session, login, guestLogin } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const target = safeReturnPath(location.state)
  const [guestError, setGuestError] = useState<unknown>(null)

  if (session.status === 'authenticated') {
    return <Navigate to={target} replace />
  }
  return (
    <section
      aria-labelledby="login-heading"
      className="mx-auto grid max-w-md gap-6 px-(--gutter) py-10 lg:max-w-5xl lg:grid-cols-2 lg:items-start"
    >
      <GlassCard depth="still" className="p-6">
        <h1 id="login-heading" className="text-3xl font-bold">
          {t('login.title')}
        </h1>
        <LoginForm
          onSubmit={async (email, password) => {
            await login(email, password)
            void navigate(target, { replace: true })
          }}
        />
      </GlassCard>

      <div className="grid gap-6">
        <GlassCard depth="still" className="p-6">
          <h2 className="text-xl font-bold">{t('login.guestTitle')}</h2>
          <p className="mt-1 text-muted-foreground">{t('login.guestBody')}</p>
          <Button
            variant="secondary"
            className="mt-4 w-full"
            onClick={() => {
              setGuestError(null)
              guestLogin().then(
                () => void navigate('/app', { replace: true }),
                (error: unknown) => {
                  setGuestError(error)
                },
              )
            }}
          >
            {t('login.guest')}
          </Button>
          {guestError !== null && (
            <div className="mt-3">
              <ErrorState error={guestError} />
            </div>
          )}
        </GlassCard>

        <GlassCard depth="still" className="p-6">
          <h2 className="text-xl font-bold">{t('login.demoTitle')}</h2>
          <p className="mt-1 text-muted-foreground">{t('login.demoBody')}</p>
          <div className="mt-4 grid gap-2 sm:grid-cols-3">
            <LinkButton to="/demo-user" variant="outline">
              {t('login.demoWorker')}
            </LinkButton>
            <LinkButton to="/demo-hr" variant="outline">
              {t('login.demoHr')}
            </LinkButton>
            <LinkButton to="/demo-admin" variant="outline">
              {t('login.demoAdmin')}
            </LinkButton>
          </div>
          <p className="mt-4 text-sm text-muted-foreground">
            <Link to="/docs" className="underline underline-offset-4">
              {t('footer.docs')}
            </Link>
          </p>
        </GlassCard>
      </div>
    </section>
  )
}
