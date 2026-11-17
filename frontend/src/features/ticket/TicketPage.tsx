import { fieldClass } from '../../components/ui/field'
import { useQuery } from '@tanstack/react-query'
import { useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { getTicket } from '../../api/account'
import { ApiError } from '../../api/client'
import { useAuthedCall } from '../../api/useAuthedCall'
import { CitationChip } from '../../components/result/CitationChip'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon } from '../../components/ui/Icon'
import { Skeleton } from '../../components/ui/Skeleton'
import { cx } from '../../lib/cx'
import { formatNumber } from '../../lib/format'
import { groupCitations, type CitationGroup } from '../../lib/citations'
import { useTitle } from '../../lib/useTitle'
import { rememberTicketCode, ticketCode } from '../../state/ticketCodes'
import { CitationSheet } from '../analysis/CitationSheet'
import type { Language } from '../../i18n'

/**
 * `/app/tickets/:ticketId` (FR-61): where a worker sees what a person decided about a finding the
 * verifier could not back. The secret code is the proof of ownership; it is never in the address.
 */
export function TicketPage() {
  const { ticketId = '' } = useParams()
  const { t } = useTranslation()
  useTitle(t('ticket.title'))
  const [code, setCode] = useState<string | null>(() => ticketCode(ticketId))
  const [typed, setTyped] = useState('')

  return (
    <div className="mx-auto max-w-3xl px-(--gutter) py-10">
      <h1 className="text-3xl font-bold">{t('ticket.title')}</h1>
      <p className="mt-2 text-muted-foreground">{t('ticket.intro')}</p>
      {code === null ? (
        <GlassCard depth="still" className="mt-6 p-5 sm:p-6">
          <form
            className="flex items-start gap-4"
            onSubmit={(event: SyntheticEvent) => {
              event.preventDefault()
              const value = typed.trim()
              if (value !== '') {
                rememberTicketCode(ticketId, value)
                setCode(value)
              }
            }}
          >
            <span
              aria-hidden="true"
              className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary/15 text-primary"
            >
              <Icon name="lock" size={22} />
            </span>
            <div className="min-w-0 flex-1">
              <label htmlFor="ticket-code" className="font-semibold">
                {t('ticket.token')}
              </label>
              <input
                id="ticket-code"
                value={typed}
                autoComplete="off"
                onChange={(event) => {
                  setTyped(event.target.value)
                }}
                className={cx(fieldClass, 'mt-1')}
              />
              <p className="mt-1 text-sm text-muted-foreground">{t('ticket.tokenHint')}</p>
              <Button type="submit" className="mt-3">
                {t('ticket.open')}
              </Button>
            </div>
          </form>
        </GlassCard>
      ) : (
        <TicketBody
          ticketId={ticketId}
          code={code}
          onWrongCode={() => {
            setCode(null)
          }}
        />
      )}
    </div>
  )
}

function TicketBody({
  ticketId,
  code,
  onWrongCode,
}: {
  ticketId: string
  code: string
  onWrongCode: () => void
}) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const call = useAuthedCall()
  const [citation, setCitation] = useState<CitationGroup | null>(null)
  const query = useQuery({
    queryKey: ['ticket', ticketId],
    queryFn: () => call((token, lang) => getTicket(ticketId, code, lang, token)),
    retry: false,
    gcTime: 5 * 60 * 1000,
  })

  if (query.isPending) {
    return (
      <div role="status" aria-label={t('ticket.loading')} className="mt-6 space-y-3">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    )
  }
  if (query.isError) {
    if (query.error instanceof ApiError && query.error.status === 404) {
      return (
        <div className="mt-6">
          <p
            role="alert"
            className="rounded-lg border border-rejected/40 bg-rejected/10 px-4 py-3 text-rejected"
          >
            {t('ticket.notFound')}
          </p>
          <Button variant="outline" className="mt-3" onClick={onWrongCode}>
            {t('ticket.token')}
          </Button>
        </div>
      )
    }
    return (
      <div className="mt-6">
        <ErrorState
          error={query.error}
          onRetry={() => {
            void query.refetch()
          }}
        />
      </div>
    )
  }
  const { items } = query.data
  const n = (value: number) => formatNumber(value, language)
  const decided = items.filter(
    (item) => item.status === 'APPROVED' || item.status === 'REJECTED',
  ).length
  return (
    <div className="mt-6">
      <GlassCard
        data-testid="ticket-summary"
        className="flex flex-wrap items-center gap-x-6 gap-y-2 p-4"
      >
        <p className="text-xl font-bold">{t('ticket.summaryCount', { count: n(items.length) })}</p>
        <p className="text-muted-foreground">{t('ticket.summaryDecided', { count: n(decided) })}</p>
        <p className="text-sm text-muted-foreground sm:ms-auto">
          {t('ticket.expires', {
            time: new Date(query.data.expiresAt).toLocaleDateString(
              language === 'bn' ? 'bn-BD' : 'en-GB',
            ),
          })}
        </p>
      </GlassCard>
      {items.length === 0 && <EmptyState title={t('ticket.empty')} />}
      <ol data-testid="ticket-timeline" className="ticket-rail mt-5 grid gap-4">
        {items.map((item, index) => (
          <li key={item.findingId ?? index}>
            <span
              aria-hidden="true"
              className={cx(
                'rail-node',
                item.status === 'APPROVED' && 'bg-verified',
                item.status === 'REJECTED' && 'bg-rejected',
                (item.status === 'OPEN' || item.status === 'IN_REVIEW') && 'bg-uncertain',
              )}
            />
            <GlassCard as="article" className="p-4">
              <div className="lift-2 flex flex-wrap items-center gap-2">
                <Badge
                  tone={
                    item.status === 'APPROVED'
                      ? 'verified'
                      : item.status === 'REJECTED'
                        ? 'rejected'
                        : item.status === 'EXPIRED'
                          ? 'neutral'
                          : 'uncertain'
                  }
                >
                  {t(`ticket.status.${item.status}`)}
                </Badge>
                {item.decision !== null && <span>{t(`ticket.decision.${item.decision}`)}</span>}
              </div>
              {item.title !== null && <h2 className="mt-2 text-lg font-bold">{item.title}</h2>}
              {item.note !== null && (
                <p className="mt-2">
                  <strong>{t('ticket.note')}: </strong>
                  {item.note}
                </p>
              )}
              {item.decidedAt !== null && (
                <p className="mt-1 text-sm text-muted-foreground">
                  {t('ticket.decidedAt', {
                    time: new Date(item.decidedAt).toLocaleString(
                      language === 'bn' ? 'bn-BD' : 'en-GB',
                    ),
                  })}
                </p>
              )}
              {item.citation !== null && item.status === 'APPROVED' && (
                <div className="mt-3">
                  {groupCitations([item.citation]).map((group) => (
                    <CitationChip
                      key={group.sectionKey}
                      group={group}
                      language={language}
                      onOpen={setCitation}
                    />
                  ))}
                </div>
              )}
            </GlassCard>
          </li>
        ))}
      </ol>
      <p className="mt-6">
        <Link to="/app" className="underline underline-offset-4">
          {t('ticket.back')}
        </Link>
      </p>
      <CitationSheet
        group={citation}
        onClose={() => {
          setCitation(null)
        }}
      />
    </div>
  )
}
