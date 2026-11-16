import { useQuery } from '@tanstack/react-query'
import { useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { getAdminTrace, type AnalysisTrace } from '../../api/admin'
import { ApiError } from '../../api/client'
import { useAuthedCall } from '../../api/useAuthedCall'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Skeleton } from '../../components/ui/Skeleton'
import { formatDateTime, formatNumber, formatUsd } from '../../lib/format'
import { useTitle } from '../../lib/useTitle'
import type { Language } from '../../i18n'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** `/admin/traces` and `/admin/traces/:analysisId` (FR-73, FR-82): the content-free trace of one analysis. */
export default function TracePage() {
  const { analysisId = '' } = useParams()
  const { t, i18n } = useTranslation()
  useTitle(t('admin.trace.title'))
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const navigate = useNavigate()
  const [typed, setTyped] = useState(analysisId)
  const [invalid, setInvalid] = useState(false)

  function open(event: SyntheticEvent) {
    event.preventDefault()
    const value = typed.trim()
    if (!UUID.test(value)) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    void navigate(`/admin/traces/${value.toLowerCase()}`)
  }

  return (
    <div>
      <h2 className="text-2xl font-bold">{t('admin.trace.title')}</h2>
      <p className="mt-1 text-muted-foreground">{t('admin.trace.intro')}</p>
      <form onSubmit={open} noValidate className="mt-4 flex flex-wrap items-end gap-3">
        <div className="min-w-0 grow sm:max-w-xl">
          <label htmlFor="trace-id" className="font-semibold">
            {t('admin.trace.idLabel')}
          </label>
          <input
            id="trace-id"
            value={typed}
            dir="ltr"
            lang="en"
            autoComplete="off"
            spellCheck={false}
            aria-invalid={invalid ? true : undefined}
            aria-describedby={invalid ? 'trace-id-error' : undefined}
            onChange={(event) => {
              setTyped(event.target.value)
            }}
            className="mt-1 block min-h-11 w-full rounded-lg border border-input bg-background/60 px-3 py-2"
          />
          {invalid && (
            <p id="trace-id-error" className="mt-1 font-semibold text-rejected">
              {t('admin.trace.invalidId')}
            </p>
          )}
        </div>
        <Button type="submit">{t('admin.trace.open')}</Button>
      </form>
      {UUID.test(analysisId) && <TraceBody analysisId={analysisId} language={language} />}
    </div>
  )
}

function TraceBody({ analysisId, language }: { analysisId: string; language: Language }) {
  const { t } = useTranslation()
  const call = useAuthedCall()
  const query = useQuery({
    queryKey: ['admin', 'trace', analysisId],
    queryFn: () => call((token, lang) => getAdminTrace(analysisId, lang, token)),
    retry: false,
    gcTime: 5 * 60 * 1000,
  })
  if (query.isPending) {
    return (
      <div role="status" aria-label={t('common.loading')} className="mt-5 space-y-3">
        <Skeleton className="h-10 w-1/2" />
        <Skeleton className="h-56 w-full" />
      </div>
    )
  }
  if (query.isError) {
    if (query.error instanceof ApiError && query.error.status === 404) {
      return (
        <p
          role="alert"
          className="mt-5 rounded-lg border border-rejected/40 bg-rejected/10 px-4 py-3 text-rejected"
        >
          {t('admin.trace.notFound')}
        </p>
      )
    }
    return (
      <div className="mt-5">
        <ErrorState
          error={query.error}
          onRetry={() => {
            void query.refetch()
          }}
        />
      </div>
    )
  }
  return <TraceView trace={query.data} language={language} />
}

export function TraceView({ trace, language }: { trace: AnalysisTrace; language: Language }) {
  const { t } = useTranslation()
  const n = (value: number | null) =>
    value === null ? t('admin.trace.none') : formatNumber(value, language)
  const dollars = (micros: number | null) =>
    micros === null ? t('admin.trace.none') : formatUsd(micros / 1_000_000, language)
  const costly = trace.events.filter((e) => (e.costUsdMicros ?? 0) > 0)
  const maxCost = Math.max(1, ...costly.map((e) => e.costUsdMicros ?? 0))
  return (
    <div className="mt-6 space-y-6">
      <p className="font-medium">
        {t('admin.trace.created', { when: formatDateTime(trace.createdAt, language) })}
      </p>

      <section aria-labelledby="trace-totals">
        <h3 id="trace-totals" className="text-xl font-bold">
          {t('admin.trace.totals')}
        </h3>
        <dl className="mt-3 grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {(
            [
              ['tokensIn', n(trace.totals.tokensIn)],
              ['tokensOut', n(trace.totals.tokensOut)],
              ['cacheRead', n(trace.totals.cacheReadTokens)],
              ['cacheWrite', n(trace.totals.cacheWriteTokens)],
              ['cost', dollars(trace.totals.costUsdMicros)],
            ] as const
          ).map(([key, value]) => (
            <GlassCard key={key} className="p-4">
              <dt className="text-sm text-muted-foreground">{t(`admin.trace.${key}`)}</dt>
              <dd className="mt-1 text-2xl font-bold">{value}</dd>
            </GlassCard>
          ))}
        </dl>
      </section>

      {costly.length > 0 && (
        <section aria-labelledby="trace-share">
          <h3 id="trace-share" className="text-xl font-bold">
            {t('admin.trace.costShare')}
          </h3>
          <ul className="mt-3 space-y-2">
            {costly.map((event, index) => (
              <li
                key={`${event.stage}-${String(index)}`}
                className="grid grid-cols-[8rem_1fr_6rem] items-center gap-3"
              >
                <code lang="en" dir="ltr" className="truncate">
                  {event.stage}
                </code>
                <span aria-hidden="true" className="h-3 rounded bg-secondary">
                  <span
                    className="block h-3 rounded bg-primary"
                    style={{
                      width: `${String(Math.max(2, ((event.costUsdMicros ?? 0) / maxCost) * 100))}%`,
                    }}
                  />
                </span>
                <span className="text-end">{dollars(event.costUsdMicros)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div
        className="glass-panel overflow-x-auto rounded-xl"
        role="region"
        aria-label={t('admin.trace.caption')}
        tabIndex={0}
      >
        <table className="w-full border-collapse text-start">
          <caption className="sr-only">{t('admin.trace.caption')}</caption>
          <thead>
            <tr className="border-b border-border">
              <th scope="col" className="px-3 py-2 text-start">
                {t('admin.trace.colStage')}
              </th>
              <th scope="col" className="px-3 py-2 text-start">
                {t('admin.trace.colModel')}
              </th>
              <th scope="col" className="px-3 py-2 text-start">
                {t('admin.trace.colPrompt')}
              </th>
              <th scope="col" className="px-3 py-2 text-end">
                {t('admin.trace.colTokens')}
              </th>
              <th scope="col" className="px-3 py-2 text-end">
                {t('admin.trace.colCost')}
              </th>
              <th scope="col" className="px-3 py-2 text-end">
                {t('admin.trace.colTime')}
              </th>
              <th scope="col" className="px-3 py-2 text-start">
                {t('admin.trace.colOutcome')}
              </th>
            </tr>
          </thead>
          <tbody>
            {trace.events.map((event, index) => (
              <tr
                key={`${event.stage}-${String(index)}`}
                className="border-b border-border align-top last:border-b-0"
              >
                <th scope="row" className="px-3 py-2 text-start font-medium">
                  <code lang="en" dir="ltr">
                    {event.stage}
                  </code>
                </th>
                <td className="px-3 py-2">
                  {event.modelId === null ? (
                    t('admin.trace.none')
                  ) : (
                    <code lang="en" dir="ltr" className="break-all">
                      {event.modelId}
                    </code>
                  )}
                </td>
                <td className="px-3 py-2">
                  {event.promptId === null ? (
                    t('admin.trace.none')
                  ) : (
                    <code lang="en" dir="ltr">
                      {event.promptId}
                      {event.promptVersion === null ? '' : ` v${String(event.promptVersion)}`}
                    </code>
                  )}
                </td>
                <td className="px-3 py-2 text-end">
                  {n(event.tokensIn)} / {n(event.tokensOut)}
                </td>
                <td className="px-3 py-2 text-end">{dollars(event.costUsdMicros)}</td>
                <td className="px-3 py-2 text-end">
                  {t('admin.trace.ms', { value: formatNumber(event.durationMs, language) })}
                </td>
                <td className="px-3 py-2">
                  <code lang="en" dir="ltr">
                    {event.outcome}
                  </code>
                  <details className="mt-1">
                    <summary className="min-h-11 cursor-pointer py-2 font-medium">
                      {t('admin.trace.detail')}
                      <span className="sr-only"> · {event.stage}</span>
                    </summary>
                    <pre
                      lang="en"
                      dir="ltr"
                      className="max-w-xs overflow-x-auto text-sm"
                      tabIndex={0}
                    >
                      {JSON.stringify(event.detail, null, 2)}
                    </pre>
                  </details>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
