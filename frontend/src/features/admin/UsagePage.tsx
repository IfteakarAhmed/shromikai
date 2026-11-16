import { useQuery } from '@tanstack/react-query'
import { Suspense, lazy, useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { getUsage } from '../../api/admin'
import { useAuthedCall } from '../../api/useAuthedCall'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Skeleton } from '../../components/ui/Skeleton'
import { formatDay, formatNumber, formatUsd } from '../../lib/format'
import { useTitle } from '../../lib/useTitle'
import { usePreferences } from '../../state/preferencesContext'
import type { Language } from '../../i18n'

const UsageCharts = lazy(() => import('./UsageCharts'))

const MAX_DAYS = 92

function daysBetween(from: string, to: string): number {
  return (
    Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1
  )
}

/** `/admin/usage` (FR-73): totals, the table and, unless the visitor saves data, the charts. */
export default function UsagePage() {
  const { t, i18n } = useTranslation()
  useTitle(t('admin.usage.title'))
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const { lowBandwidth } = usePreferences()
  const call = useAuthedCall()
  const [range, setRange] = useState<{ from: string; to: string } | null>(null)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [rangeError, setRangeError] = useState(false)
  const query = useQuery({
    queryKey: ['admin', 'usage', range?.from ?? 'default', range?.to ?? 'default'],
    queryFn: () => call((token, lang) => getUsage(range, lang, token)),
    gcTime: 5 * 60 * 1000,
  })
  const report = query.data
  const shownFrom = from === '' ? (report?.from ?? '') : from
  const shownTo = to === '' ? (report?.to ?? '') : to

  function apply(event: SyntheticEvent) {
    event.preventDefault()
    const length = daysBetween(shownFrom, shownTo)
    if (shownFrom === '' || shownTo === '' || length < 1 || length > MAX_DAYS) {
      setRangeError(true)
      return
    }
    setRangeError(false)
    setRange({ from: shownFrom, to: shownTo })
  }

  const n = (value: number) => formatNumber(value, language)
  return (
    <div>
      <h2 className="text-2xl font-bold">{t('admin.usage.title')}</h2>
      <p className="mt-1 text-muted-foreground">{t('admin.usage.intro')}</p>

      <form onSubmit={apply} noValidate className="mt-4 flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="block font-semibold">{t('admin.usage.from')}</span>
          <input
            type="date"
            value={shownFrom}
            onChange={(event) => {
              setFrom(event.target.value)
            }}
            className="mt-1 min-h-11 rounded-lg border border-input bg-background px-3"
          />
        </label>
        <label className="block">
          <span className="block font-semibold">{t('admin.usage.to')}</span>
          <input
            type="date"
            value={shownTo}
            onChange={(event) => {
              setTo(event.target.value)
            }}
            className="mt-1 min-h-11 rounded-lg border border-input bg-background px-3"
          />
        </label>
        <Button type="submit">{t('admin.usage.apply')}</Button>
      </form>
      {rangeError && (
        <p role="alert" className="mt-2 font-semibold text-rejected">
          {t('admin.usage.rangeError')}
        </p>
      )}

      {query.isPending && (
        <div role="status" aria-label={t('common.loading')} className="mt-5 space-y-3">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      )}
      {query.isError && (
        <div className="mt-5">
          <ErrorState
            error={query.error}
            onRetry={() => {
              void query.refetch()
            }}
          />
        </div>
      )}
      {report !== undefined && (
        <>
          <h3 className="mt-6 text-xl font-bold">{t('admin.usage.totals')}</h3>
          <ul className="mt-3 grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {(
              [
                ['analyses', n(report.totals.analyses)],
                ['compares', n(report.totals.compares)],
                ['memoRenders', n(report.totals.memoRenders)],
                ['apiCalls', n(report.totals.apiCalls)],
                ['mcpCalls', n(report.totals.mcpCalls)],
                ['cost', formatUsd(report.totals.costUsd, language)],
              ] as const
            ).map(([key, value]) => (
              <li key={key}>
                <GlassCard className="h-full p-4">
                  <p className="text-sm text-muted-foreground">{t(`admin.usage.${key}`)}</p>
                  <p className="mt-1 text-2xl font-bold">{value}</p>
                </GlassCard>
              </li>
            ))}
          </ul>

          <div className="mt-6">
            {lowBandwidth ? (
              <p className="rounded-lg border border-border bg-secondary/60 p-3">
                {t('admin.usage.lowBandwidth')}
              </p>
            ) : (
              <Suspense
                fallback={
                  <div role="status" aria-label={t('admin.usage.chartLoading')}>
                    <Skeleton className="h-72 w-full" />
                  </div>
                }
              >
                <UsageCharts days={report.days} language={language} />
              </Suspense>
            )}
          </div>

          <h3 className="mt-8 text-xl font-bold">{t('admin.usage.tableTitle')}</h3>
          <div
            className="glass-panel mt-3 overflow-x-auto rounded-xl"
            role="region"
            aria-label={t('admin.usage.tableTitle')}
            tabIndex={0}
          >
            <table className="w-full border-collapse text-start">
              <caption className="sr-only">
                {t('admin.usage.perDaySummary', {
                  from: formatDay(report.from, language),
                  to: formatDay(report.to, language),
                })}
              </caption>
              <thead>
                <tr className="border-b border-border">
                  <th scope="col" className="px-3 py-2 text-start">
                    {t('admin.usage.colDate')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-end">
                    {t('admin.usage.analyses')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-end">
                    {t('admin.usage.compares')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-end">
                    {t('admin.usage.memoRenders')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-end">
                    {t('admin.usage.apiCalls')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-end">
                    {t('admin.usage.mcpCalls')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-end">
                    {t('admin.usage.cost')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {report.days.map((day) => (
                  <tr key={day.date} className="border-b border-border last:border-b-0">
                    <th scope="row" className="px-3 py-2 text-start font-medium">
                      {formatDay(day.date, language)}
                    </th>
                    <td className="px-3 py-2 text-end">{n(day.analyses)}</td>
                    <td className="px-3 py-2 text-end">{n(day.compares)}</td>
                    <td className="px-3 py-2 text-end">{n(day.memoRenders)}</td>
                    <td className="px-3 py-2 text-end">{n(day.apiCalls)}</td>
                    <td className="px-3 py-2 text-end">{n(day.mcpCalls)}</td>
                    <td className="px-3 py-2 text-end">{formatUsd(day.costUsd, language)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h3 className="mt-8 text-xl font-bold">{t('admin.usage.costByModel')}</h3>
          {Object.keys(report.totals.costByModel).length === 0 ? (
            <p className="mt-2 text-muted-foreground">{t('admin.usage.noCost')}</p>
          ) : (
            <ul className="mt-3 grid gap-2 sm:grid-cols-2">
              {Object.entries(report.totals.costByModel).map(([model, cost]) => (
                <li
                  key={model}
                  className="glass-panel flex items-center justify-between gap-3 rounded-lg px-4 py-3"
                >
                  <code lang="en" dir="ltr" className="break-all">
                    {model}
                  </code>
                  <span className="font-semibold">{formatUsd(cost, language)}</span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}
