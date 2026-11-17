import { cx } from '../../lib/cx'
import { fieldInlineClass } from '../../components/ui/field'
import { TableShell } from '../../components/ui/TableShell'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { listReviewItems, type ReviewItem } from '../../api/admin'
import { useAuthedCall } from '../../api/useAuthedCall'
import { SeverityTag } from '../../components/result/SeverityTag'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Skeleton } from '../../components/ui/Skeleton'
import { formatDateTime, formatNumber } from '../../lib/format'
import { useTitle } from '../../lib/useTitle'
import { useMediaQuery } from '../landing/useCapabilities'
import { ReviewDetail } from './ReviewDetail'
import { ReviewStatusBadge } from './ReviewStatusBadge'
import { useItemTitle } from './useItemTitle'
import type { Language } from '../../i18n'

const STATUSES = ['OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED'] as const
const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const

/**
 * `/admin/review` and `/admin/review/:id` (FR-60): the queue as a table with the open item in a
 * panel beside it; on a phone the item takes the whole screen. Nothing shown holds contract text.
 */
export default function ReviewPage() {
  const { id } = useParams()
  const { t, i18n } = useTranslation()
  useTitle(t('admin.review.title'))
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const wide = useMediaQuery('(min-width: 1024px)')
  const [status, setStatus] = useState('OPEN')
  const [severity, setSeverity] = useState('')
  const [page, setPage] = useState(0)
  const call = useAuthedCall()
  const list = useQuery({
    queryKey: ['admin', 'review', 'list', status, severity, page],
    queryFn: () => call((token, lang) => listReviewItems({ status, severity, page }, lang, token)),
    gcTime: 5 * 60 * 1000,
  })

  const showList = wide || id === undefined
  const showDetail = id !== undefined
  const pages =
    list.data === undefined ? 1 : Math.max(1, Math.ceil(list.data.totalItems / list.data.size))

  return (
    <div>
      <h2 className="text-2xl font-bold">{t('admin.review.title')}</h2>
      <p className="mt-1 text-muted-foreground">{t('admin.review.intro')}</p>
      <div
        className={
          showList && showDetail
            ? 'mt-5 grid items-start gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]'
            : 'mt-5'
        }
      >
        {showList && (
          <section aria-label={t('admin.review.caption')}>
            <form
              className="flex flex-wrap items-end gap-3"
              role="search"
              aria-label={t('admin.review.filters')}
              onSubmit={(event) => {
                event.preventDefault()
              }}
            >
              <label className="block">
                <span className="block font-semibold">{t('admin.review.status')}</span>
                <select
                  value={status}
                  onChange={(event) => {
                    setStatus(event.target.value)
                    setPage(0)
                  }}
                  className={cx(fieldInlineClass, 'mt-1')}
                >
                  <option value="">{t('admin.review.all')}</option>
                  {STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {t(`admin.review.statuses.${s}`)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="block font-semibold">{t('admin.review.severity')}</span>
                <select
                  value={severity}
                  onChange={(event) => {
                    setSeverity(event.target.value)
                    setPage(0)
                  }}
                  className={cx(fieldInlineClass, 'mt-1')}
                >
                  <option value="">{t('admin.review.all')}</option>
                  {SEVERITIES.map((s) => (
                    <option key={s} value={s}>
                      {t(`severity.${s.toLowerCase()}`)}
                    </option>
                  ))}
                </select>
              </label>
              {list.data !== undefined && (
                <p className="pb-2 font-medium" role="status">
                  {t('admin.review.count', { count: formatNumber(list.data.totalItems, language) })}
                </p>
              )}
            </form>

            <div className="mt-4">
              {list.isPending && (
                <div role="status" aria-label={t('common.loading')} className="space-y-3">
                  <Skeleton className="h-14 w-full" />
                  <Skeleton className="h-14 w-full" />
                  <Skeleton className="h-14 w-full" />
                </div>
              )}
              {list.isError && (
                <ErrorState
                  error={list.error}
                  onRetry={() => {
                    void list.refetch()
                  }}
                />
              )}
              {list.data !== undefined && list.data.items.length === 0 && (
                <GlassCard>
                  <EmptyState
                    title={t('admin.review.empty')}
                    description={t('admin.review.emptyHelp')}
                    icon="list"
                  />
                </GlassCard>
              )}
              {list.data !== undefined && list.data.items.length > 0 && (
                <ItemList items={list.data.items} selected={id} language={language} />
              )}
            </div>

            {list.data !== undefined && pages > 1 && (
              <nav
                aria-label={t('admin.review.caption')}
                className="mt-4 flex items-center justify-between gap-3"
              >
                <Button
                  variant="outline"
                  disabled={page === 0}
                  onClick={() => {
                    setPage((p) => Math.max(0, p - 1))
                  }}
                >
                  {t('admin.review.previous')}
                </Button>
                <span>
                  {t('admin.review.page', {
                    page: formatNumber(page + 1, language),
                    pages: formatNumber(pages, language),
                  })}
                </span>
                <Button
                  variant="outline"
                  disabled={page + 1 >= pages}
                  onClick={() => {
                    setPage((p) => p + 1)
                  }}
                >
                  {t('admin.review.next')}
                </Button>
              </nav>
            )}
          </section>
        )}

        {showDetail ? (
          <ReviewDetail id={id} language={language} />
        ) : (
          wide && (
            <GlassCard className="p-5">
              <p className="text-muted-foreground">{t('admin.review.select')}</p>
            </GlassCard>
          )
        )}
      </div>
    </div>
  )
}

function ItemList({
  items,
  selected,
  language,
}: {
  items: ReviewItem[]
  selected: string | undefined
  language: Language
}) {
  const { t } = useTranslation()
  const title = useItemTitle()
  const navigate = useNavigate()
  const wideTable = useMediaQuery('(min-width: 768px)')

  if (!wideTable) {
    return (
      <ul className="space-y-3">
        {items.map((item) => (
          <li key={item.id}>
            <GlassCard className="p-4">
              <div className="flex flex-wrap items-center gap-2">
                <ReviewStatusBadge status={item.status} />
                {item.severity !== null && <SeverityTag severity={item.severity} />}
              </div>
              <p className="mt-2 font-semibold">{title(item)}</p>
              <p className="text-sm text-muted-foreground">
                {formatDateTime(item.createdAt, language)}
              </p>
              <Link
                to={`/admin/review/${item.id}`}
                className="mt-3 inline-flex min-h-11 items-center font-semibold underline underline-offset-4"
              >
                {t('admin.review.open')}
                <span className="sr-only"> · {title(item)}</span>
              </Link>
            </GlassCard>
          </li>
        ))}
      </ul>
    )
  }
  return (
    <TableShell label={t('admin.review.caption')}>
      <caption className="sr-only">{t('admin.review.caption')}</caption>
      <thead>
        <tr className="border-b border-border text-start">
          <th scope="col" className="px-4 py-3 text-start">
            {t('admin.review.colClause')}
          </th>
          <th scope="col" className="px-4 py-3 text-start">
            {t('admin.review.colSeverity')}
          </th>
          <th scope="col" className="px-4 py-3 text-start">
            {t('admin.review.colStatus')}
          </th>
          <th scope="col" className="px-4 py-3 text-start">
            {t('admin.review.colCreated')}
          </th>
          <th scope="col" className="px-4 py-3 text-start">
            {t('admin.review.colAction')}
          </th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr
            key={item.id}
            aria-current={item.id === selected ? 'true' : undefined}
            className={
              item.id === selected
                ? 'border-b border-border bg-primary/10'
                : 'border-b border-border last:border-b-0'
            }
          >
            <th scope="row" className="px-4 py-3 text-start font-semibold">
              {title(item)}
            </th>
            <td className="px-4 py-3">
              {item.severity === null ? '–' : <SeverityTag severity={item.severity} />}
            </td>
            <td className="px-4 py-3">
              <ReviewStatusBadge status={item.status} />
            </td>
            <td className="px-4 py-3">{formatDateTime(item.createdAt, language)}</td>
            <td className="px-4 py-3">
              <Button
                variant="outline"
                onClick={() => {
                  void navigate(`/admin/review/${item.id}`)
                }}
              >
                {t('admin.review.open')}
                <span className="sr-only"> · {title(item)}</span>
              </Button>
            </td>
          </tr>
        ))}
      </tbody>
    </TableShell>
  )
}
