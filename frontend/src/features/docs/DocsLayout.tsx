import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router-dom'
import { Skeleton } from '../../components/ui/Skeleton'
import { cx } from '../../lib/cx'

const PAGES = [
  { to: '/docs', key: 'index', end: true },
  { to: '/docs/architecture', key: 'architecture', end: false },
  { to: '/docs/data', key: 'data', end: false },
  { to: '/docs/security', key: 'security', end: false },
  { to: '/docs/audit', key: 'audit', end: false },
  { to: '/docs/evaluation', key: 'evaluation', end: false },
  { to: '/docs/prompts', key: 'prompts', end: false },
  { to: '/docs/mcp', key: 'mcp', end: false },
  { to: '/docs/known-gaps', key: 'gaps', end: false },
] as const

/** The frame of the docs hub (FR-74): a heading, the pages and the page itself. */
export default function DocsLayout() {
  const { t } = useTranslation()
  return (
    <div className="mx-auto max-w-5xl px-(--gutter) py-8">
      <nav aria-label={t('docs.navLabel')} className="flex flex-wrap gap-2">
        {PAGES.map((page) => (
          <NavLink
            key={page.key}
            to={page.to}
            end={page.end}
            className={({ isActive }) =>
              cx(
                'inline-flex min-h-11 items-center rounded-lg border px-4 py-2 font-semibold',
                isActive
                  ? 'border-primary bg-primary text-primary-foreground'
                  : 'border-border bg-secondary/60 text-foreground hover:bg-secondary',
              )
            }
          >
            {t(`docs.nav.${page.key}`)}
          </NavLink>
        ))}
      </nav>
      <div className="mt-6">
        <Suspense
          fallback={
            <div aria-hidden="true" className="space-y-4">
              <Skeleton className="h-9 w-1/3" />
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
