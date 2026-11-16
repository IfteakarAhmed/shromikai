import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router-dom'
import { Skeleton } from '../../components/ui/Skeleton'
import { cx } from '../../lib/cx'
import { useTitle } from '../../lib/useTitle'

const SECTIONS = [
  { to: '/admin', key: 'overview', end: true },
  { to: '/admin/review', key: 'review', end: false },
  { to: '/admin/playbooks', key: 'playbooks', end: false },
  { to: '/admin/api-keys', key: 'keys', end: false },
  { to: '/admin/usage', key: 'usage', end: false },
  { to: '/admin/traces', key: 'trace', end: false },
] as const

/** The frame of the admin console (FR-73): a heading, the sections and the page of the section. */
export default function AdminLayout() {
  const { t } = useTranslation()
  useTitle(t('admin.title'))
  return (
    <div className="mx-auto max-w-6xl px-(--gutter) py-8">
      <h1 className="text-3xl font-bold">{t('admin.title')}</h1>
      <nav aria-label={t('admin.nav.label')} className="mt-4 flex flex-wrap gap-2">
        {SECTIONS.map((section) => (
          <NavLink
            key={section.key}
            to={section.to}
            end={section.end}
            className={({ isActive }) =>
              cx(
                'inline-flex min-h-11 items-center rounded-lg border px-4 py-2 font-semibold',
                isActive
                  ? 'border-primary bg-primary text-primary-foreground'
                  : 'border-border bg-secondary/60 text-foreground hover:bg-secondary',
              )
            }
          >
            {t(`admin.nav.${section.key}`)}
          </NavLink>
        ))}
      </nav>
      <div className="mt-6">
        <Suspense
          fallback={
            <div aria-hidden="true" className="space-y-4">
              <Skeleton className="h-8 w-1/3" />
              <Skeleton className="h-48 w-full" />
            </div>
          }
        >
          <Outlet />
        </Suspense>
      </div>
    </div>
  )
}
