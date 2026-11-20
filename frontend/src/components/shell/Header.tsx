import { useEffect, useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, useLocation, useNavigate, type NavLinkProps } from 'react-router-dom'
import { isPlainClick, withViewTransition } from '../../lib/viewTransition'
import { usePreferences } from '../../state/preferencesContext'
import { isRole } from '../../api/auth'
import { useAuth } from '../../auth/authContext'
import { cx } from '../../lib/cx'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'
import { BrandMark } from '../brand/BrandMark'
import { Wordmark } from '../brand/Wordmark'
import { isStaticReplay } from '../../api/replay'
import { LanguageToggle } from './LanguageToggle'
import { LowBandwidthToggle } from './LowBandwidthToggle'
import { ThemeToggle } from './ThemeToggle'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  cx(
    'inline-flex min-h-14 shrink-0 items-center whitespace-nowrap rounded-xl px-3 font-semibold transition-colors xl:min-h-11',
    isActive ? 'bg-accent text-foreground' : 'text-muted-foreground hover:text-foreground',
  )

/** A navigation link whose same-tab clicks run inside a View Transition (C1). */
function NavItem({
  to,
  needsServer = false,
  ...props
}: NavLinkProps & { to: string; needsServer?: boolean }) {
  const navigate = useNavigate()
  const { lowBandwidth } = usePreferences()
  // The static replay has no server for this page: the link is not shown at all.
  if (needsServer && isStaticReplay()) {
    return null
  }
  return (
    <NavLink
      to={to}
      {...props}
      onClick={(event) => {
        if (!isPlainClick(event)) {
          return
        }
        event.preventDefault()
        withViewTransition(() => {
          void navigate(to)
        }, lowBandwidth)
      }}
    />
  )
}

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
  // The static replay has no accounts: no sign-in, sign-out or signed-in line (FR-85).
  const replay = isStaticReplay()
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
    <header className="site-header glass-panel sticky top-0 z-30 rounded-none border-x-0 border-t-0">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-(--gutter) py-2">
        <Link
          to="/"
          className="inline-flex min-h-14 shrink-0 items-center gap-2.5 whitespace-nowrap rounded-lg xl:min-h-11"
        >
          <BrandMark className="size-10" />
          <Wordmark className="text-2xl" />
        </Link>

        <nav aria-label={t('nav.label')} className="hidden min-w-0 items-center gap-1 xl:flex">
          <NavItem to="/app" end className={linkClass}>
            {t('nav.analyse')}
          </NavItem>
          <NavItem to="/app/compare" className={linkClass} needsServer>
            {t('nav.compare')}
          </NavItem>
          <NavItem to="/docs" className={linkClass}>
            {t('nav.docs')}
          </NavItem>
          {user !== null && (
            <NavItem to="/app/profile" className={linkClass} needsServer>
              {t('nav.profile')}
            </NavItem>
          )}
          {role === 'HR' && (
            <NavItem to="/app/playbooks" className={linkClass} needsServer>
              {t('nav.playbooks')}
            </NavItem>
          )}
          {role === 'ADMIN' && (
            <NavItem to="/admin" className={linkClass} needsServer>
              {t('nav.admin')}
            </NavItem>
          )}
        </nav>

        <div className="flex shrink-0 items-center gap-2">
          <div className="hidden items-center gap-2 xl:flex">
            <LowBandwidthToggle />
            <ThemeToggle />
          </div>
          <LanguageToggle />
          {replay ? null : user === null ? (
            <Link
              to="/login"
              className="hidden min-h-11 shrink-0 items-center whitespace-nowrap rounded-xl border border-border px-4 font-semibold hover:bg-accent xl:inline-flex"
            >
              {t('nav.signIn')}
            </Link>
          ) : (
            <div className="hidden xl:block">
              <Button
                variant="outline"
                className="whitespace-nowrap"
                onClick={() => void signOut()}
              >
                <Icon name="logout" />
                {t('nav.signOut')}
              </Button>
            </div>
          )}
          <div className="xl:hidden">
            <Button
              variant="outline"
              size="icon"
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
      </div>

      {!replay && session.status !== 'loading' && session.status !== 'anonymous' && (
        <p className="mx-auto max-w-6xl px-(--gutter) pb-2 text-sm text-muted-foreground">
          {user === null
            ? t('nav.guestAs')
            : `${t('nav.signedInAs', { email: user.email ?? t(`nav.role.${role ?? 'WORKER'}`) })} (${t(`nav.role.${role ?? 'WORKER'}`)})`}
        </p>
      )}

      <div
        id={menuId}
        hidden={!open}
        className="border-t border-border px-(--gutter) py-3 xl:hidden"
      >
        <nav aria-label={t('nav.label')} className="mx-auto flex max-w-6xl flex-col gap-1">
          <NavItem to="/app" end className={linkClass}>
            {t('nav.analyse')}
          </NavItem>
          <NavItem to="/app/compare" className={linkClass} needsServer>
            {t('nav.compare')}
          </NavItem>
          <NavItem to="/docs" className={linkClass}>
            {t('nav.docs')}
          </NavItem>
          {user !== null && (
            <NavItem to="/app/profile" className={linkClass} needsServer>
              {t('nav.profile')}
            </NavItem>
          )}
          {role === 'HR' && (
            <NavItem to="/app/playbooks" className={linkClass} needsServer>
              {t('nav.playbooks')}
            </NavItem>
          )}
          {role === 'ADMIN' && (
            <NavItem to="/admin" className={linkClass} needsServer>
              {t('nav.admin')}
            </NavItem>
          )}
          <div className="mt-2 flex flex-wrap gap-2">
            <LowBandwidthToggle compact={false} />
            <ThemeToggle />
            {replay ? null : user === null ? (
              <Link
                to="/login"
                className="inline-flex min-h-14 items-center whitespace-nowrap rounded-xl border border-border px-4 font-semibold hover:bg-accent"
              >
                {t('nav.signIn')}
              </Link>
            ) : (
              <Button
                variant="outline"
                className="whitespace-nowrap"
                onClick={() => void signOut()}
              >
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
