import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { getUsage, listReviewItems, type UsageReport } from '../../api/admin'
import { useAuthedCall } from '../../api/useAuthedCall'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Skeleton } from '../../components/ui/Skeleton'
import { formatDecimal, formatNumber, formatPercent, formatRatio } from '../../lib/format'
import type { Language } from '../../i18n'

const LINKS = [
  { to: '/admin/review', key: 'review' },
  { to: '/admin/playbooks', key: 'playbooks' },
  { to: '/admin/api-keys', key: 'keys' },
  { to: '/admin/usage', key: 'usage' },
  { to: '/admin/traces', key: 'trace' },
] as const

/** `/admin`: the three measures of PRD §10 and the way into each admin page (FR-73). */
export default function AdminHome() {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const call = useAuthedCall()
  const usage = useQuery({
    queryKey: ['admin', 'usage', 'default'],
    queryFn: () => call((token, lang) => getUsage(null, lang, token)),
    gcTime: 5 * 60 * 1000,
  })
  const open = useQuery({
    queryKey: ['admin', 'review', 'open-count'],
    queryFn: () =>
      call((token, lang) =>
        listReviewItems({ status: 'OPEN', severity: '', page: 0 }, lang, token),
      ),
    gcTime: 5 * 60 * 1000,
  })

  return (
    <div>
      <p className="text-muted-foreground">{t('admin.overview.intro')}</p>
      <h2 className="mt-6 text-xl font-bold">{t('admin.overview.kpiTitle')}</h2>
      {usage.isPending && (
        <div
          role="status"
          aria-label={t('common.loading')}
          className="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
        >
          {[0, 1, 2, 3].map((n) => (
            <Skeleton key={n} className="h-36 w-full" />
          ))}
        </div>
      )}
      {usage.isError && (
        <div className="mt-3">
          <ErrorState
            error={usage.error}
            onRetry={() => {
              void usage.refetch()
            }}
          />
        </div>
      )}
      {usage.data !== undefined && (
        <ul className="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Kpis report={usage.data} language={language} open={open.data?.totalItems ?? null} />
        </ul>
      )}

      <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {LINKS.map((link) => (
          <li key={link.key}>
            <GlassCard interactive className="h-full p-5">
              <h3 className="text-lg font-bold">
                <Link to={link.to} className="underline-offset-4 hover:underline">
                  {t(`admin.nav.${link.key}`)}
                </Link>
              </h3>
              <p className="mt-2 text-muted-foreground">{t(`admin.overview.${link.key}`)}</p>
            </GlassCard>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Kpis({
  report,
  language,
  open,
}: {
  report: UsageReport
  language: Language
  open: number | null
}) {
  const { t } = useTranslation()
  const { completionRate, memoDownloadRate, reviewTurnaroundHoursMedian } = report.kpis
  const n = (value: number) => formatNumber(value, language)
  return (
    <>
      <Tile
        title={t('admin.kpi.completion')}
        help={t('admin.kpi.completionHelp')}
        value={completionRate.value === null ? null : formatPercent(completionRate.value, language)}
        basis={t('admin.kpi.basis', { count: n(completionRate.count), n: n(completionRate.n) })}
      />
      <Tile
        title={t('admin.kpi.memo')}
        help={t('admin.kpi.memoHelp')}
        value={
          memoDownloadRate.value === null
            ? null
            : t('admin.kpi.ratio', { value: formatRatio(memoDownloadRate.value, language) })
        }
        basis={t('admin.kpi.basis', { count: n(memoDownloadRate.count), n: n(memoDownloadRate.n) })}
      />
      <Tile
        title={t('admin.kpi.review')}
        help={t('admin.kpi.reviewHelp')}
        value={
          reviewTurnaroundHoursMedian.value === null
            ? null
            : t('admin.kpi.hours', {
                value: formatDecimal(reviewTurnaroundHoursMedian.value, language, 1),
              })
        }
        basis={t('admin.kpi.basisN', { n: n(reviewTurnaroundHoursMedian.n) })}
      />
      <Tile
        title={t('admin.kpi.open')}
        help={t('admin.overview.review')}
        value={open === null ? null : n(open)}
        basis={null}
      />
    </>
  )
}

function Tile({
  title,
  help,
  value,
  basis,
}: {
  title: string
  help: string
  value: string | null
  basis: string | null
}) {
  const { t } = useTranslation()
  return (
    <li>
      <GlassCard className="h-full p-5">
        <h3 className="font-semibold text-muted-foreground">{title}</h3>
        <p className="mt-2 text-3xl font-bold" data-testid="kpi-value">
          {value ?? t('admin.kpi.noData')}
        </p>
        {basis !== null && <p className="mt-1 font-medium">{basis}</p>}
        <p className="mt-2 text-sm text-muted-foreground">{help}</p>
      </GlassCard>
    </li>
  )
}
