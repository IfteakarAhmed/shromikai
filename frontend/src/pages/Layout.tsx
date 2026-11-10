import { useTranslation } from 'react-i18next'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { isRole } from '../api/auth'
import { useAuth } from '../auth/authContext'
import { rememberLanguage, type Language } from '../i18n'

const buttonClass =
  'min-h-11 min-w-11 rounded-md border border-stone-300 bg-white px-3 py-2 text-sm font-medium hover:bg-stone-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700'
const linkClass =
  'inline-flex min-h-11 items-center px-2 py-2 font-medium underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700'

/** Shell with a role-aware menu, the language switch, sign out and the legal disclaimer. */
export function Layout() {
  const { t, i18n } = useTranslation()
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const user = session.status === 'authenticated' ? session.user : null
  const role = user !== null && isRole(user.role) ? user.role : null

  function toggleLanguage() {
    const next: Language = language === 'bn' ? 'en' : 'bn'
    rememberLanguage(next)
    void i18n.changeLanguage(next)
    document.documentElement.lang = next
  }

  async function signOut() {
    await logout()
    void navigate('/login', { replace: true })
  }

  return (
    <div lang={language} className="mx-auto max-w-2xl px-4 py-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/"
          className="text-xl font-bold focus-visible:outline-2 focus-visible:outline-emerald-700"
        >
          {t('app.name')}
        </Link>
        <nav aria-label={t('nav.label')} className="flex flex-wrap items-center gap-1">
          {role !== null && (
            <NavLink to="/app" className={linkClass}>
              {t('nav.analyse')}
            </NavLink>
          )}
          {role === 'ADMIN' && (
            <NavLink to="/admin" className={linkClass}>
              {t('nav.admin')}
            </NavLink>
          )}
          <button
            type="button"
            onClick={toggleLanguage}
            aria-label={t('language.switchLabel')}
            className={buttonClass}
          >
            <span lang={language === 'bn' ? 'en' : 'bn'}>{t('language.switch')}</span>
          </button>
          {user !== null && (
            <button type="button" onClick={() => void signOut()} className={buttonClass}>
              {t('nav.signOut')}
            </button>
          )}
        </nav>
      </header>
      {user !== null && role !== null && (
        <p className="mt-2 text-sm text-stone-600">
          {t('nav.signedInAs', { email: user.email ?? t(`nav.role.${role}`) })} (
          {t(`nav.role.${role}`)})
        </p>
      )}
      <main className="py-8">
        <Outlet />
      </main>
      <footer className="text-sm text-stone-600">
        <p>{t('app.disclaimer')}</p>
      </footer>
    </div>
  )
}
