import { useEffect, useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { isRole } from '../../api/auth'
import { useAuth } from '../../auth/authContext'
import { cx } from '../../lib/cx'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'
import { LanguageToggle } from './LanguageToggle'
import { LowBandwidthToggle } from './LowBandwidthToggle'
import { ThemeToggle } from './ThemeToggle'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  cx(
    'inline-flex min-h-11 items-center rounded-lg px-3 font-semibold transition-colors',
    isActive ? 'bg-accent text-foreground' : 'text-muted-foreground hover:text-foreground',
  )

/** The sticky header: brand, role-aware menu and the preference toggles (FR-68, FR-75, FR-76). */
export function Header() {
  const { t } = useTranslation()
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  // The menu is open for one page only: moving to another page closes it.
  const [openAt, setOpenAt] = useState<string | null>(null)
  const open = openAt === location.pathname
  const menuId = useId()
  const user = session.status === 'authenticated' ? session.user : null
  const role = user !== null && isRole(user.role) ? user.role : null

  useEffect(() => {
    if (!open) {
      return undefined
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpenAt(null)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  async function signOut() {
    await logout()
    void navigate('/login', { replace: true })
  }

  return (
    <header className="glass-panel sticky top-0 z-30 rounded-none border-x-0 border-t-0">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-(--gutter) py-2">
        <Link
          to="/"
          className="inline-flex min-h-11 items-center gap-2 rounded-lg text-xl font-bold"
        >
          <span className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Icon name="shield" size={20} />
          </span>
          <span>{t('app.name')}</span>
        </Link>

        <nav aria-label={t('nav.label')} className="hidden items-center gap-1 lg:flex">
          <NavLink to="/app" end className={linkClass}>
            {t('nav.analyse')}
          </NavLink>
          <NavLink to="/app/compare" className={linkClass}>
            {t('nav.compare')}
          </NavLink>
          <NavLink to="/docs" className={linkClass}>
            {t('nav.docs')}
          </NavLink>
          {user !== null && (
            <NavLink to="/app/profile" className={linkClass}>
              {t('nav.profile')}
            </NavLink>
          )}
          {role === 'ADMIN' && (
            <NavLink to="/admin" className={linkClass}>
              {t('nav.admin')}
            </NavLink>
          )}
        </nav>

        <div className="flex items-center gap-2">
          <div className="hidden items-center gap-2 lg:flex">
            <LowBandwidthToggle />
            <ThemeToggle />
          </div>
          <LanguageToggle />
          {user === null ? (
            <Link
              to="/login"
              className="hidden min-h-11 items-center rounded-lg border border-border px-4 font-semibold hover:bg-accent lg:inline-flex"
            >
              {t('nav.signIn')}
            </Link>
          ) : (
            <Button
              variant="outline"
              className="hidden lg:inline-flex"
              onClick={() => void signOut()}
            >
              <Icon name="logout" />
              {t('nav.signOut')}
            </Button>
          )}
          <Button
            variant="outline"
            size="icon"
            className="lg:hidden"
            aria-expanded={open}
            aria-controls={menuId}
            aria-label={t('nav.menu')}
            onClick={() => {
              setOpenAt(open ? null : location.pathname)
            }}
          >
            <Icon name={open ? 'x' : 'menu'} />
          </Button>
        </div>
      </div>

      {session.status !== 'loading' && session.status !== 'anonymous' && (
        <p className="mx-auto max-w-6xl px-(--gutter) pb-2 text-sm text-muted-foreground">
          {user === null
            ? t('nav.guestAs')
            : `${t('nav.signedInAs', { email: user.email ?? t(`nav.role.${role ?? 'WORKER'}`) })} (${t(`nav.role.${role ?? 'WORKER'}`)})`}
        </p>
      )}

      <div
        id={menuId}
        hidden={!open}
        className="border-t border-border px-(--gutter) py-3 lg:hidden"
      >
        <nav aria-label={t('nav.label')} className="mx-auto flex max-w-6xl flex-col gap-1">
          <NavLink to="/app" end className={linkClass}>
            {t('nav.analyse')}
          </NavLink>
          <NavLink to="/app/compare" className={linkClass}>
            {t('nav.compare')}
          </NavLink>
          <NavLink to="/docs" className={linkClass}>
            {t('nav.docs')}
          </NavLink>
          {user !== null && (
            <NavLink to="/app/profile" className={linkClass}>
              {t('nav.profile')}
            </NavLink>
          )}
          {role === 'ADMIN' && (
            <NavLink to="/admin" className={linkClass}>
              {t('nav.admin')}
            </NavLink>
          )}
          <div className="mt-2 flex flex-wrap gap-2">
            <LowBandwidthToggle />
            <ThemeToggle />
            {user === null ? (
              <Link
                to="/login"
                className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 font-semibold hover:bg-accent"
              >
                {t('nav.signIn')}
              </Link>
            ) : (
              <Button variant="outline" onClick={() => void signOut()}>
                <Icon name="logout" />
                {t('nav.signOut')}
              </Button>
            )}
          </div>
        </nav>
      </div>
    </header>
  )
}
