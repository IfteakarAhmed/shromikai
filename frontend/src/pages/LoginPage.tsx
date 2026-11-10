import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/authContext'
import { LoginForm } from '../auth/LoginForm'

/** Only same-site paths are accepted as a return address, never a full URL. */
function safeReturnPath(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/app'
}

export function LoginPage() {
  const { t } = useTranslation()
  const { session, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const target = safeReturnPath(location.state)

  if (session.status === 'authenticated') {
    return <Navigate to={target} replace />
  }
  return (
    <section aria-labelledby="login-heading">
      <h1 id="login-heading" className="text-2xl font-bold">
        {t('login.title')}
      </h1>
      <LoginForm
        onSubmit={async (email, password) => {
          await login(email, password)
          void navigate(target, { replace: true })
        }}
      />
    </section>
  )
}
