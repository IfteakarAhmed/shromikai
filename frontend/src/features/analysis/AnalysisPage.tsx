import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useParams } from 'react-router-dom'
import { getAnalysis, isResult, pollAnalysis } from '../../api/analyses'
import { ApiError, type Problem } from '../../api/client'
import { watchEvents, type Stage } from '../../api/sse'
import { useAuthedCall } from '../../api/useAuthedCall'
import { useAuth } from '../../auth/authContext'
import { ErrorState } from '../../components/ui/ErrorState'
import { LinkButton } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { stepStates } from '../../lib/stages'
import { useTitle } from '../../lib/useTitle'
import { ProgressView } from './ProgressView'
import { watchTiming } from './timing'
import { ResultsView } from './ResultsView'

interface WatchState {
  seen: Stage[]
  progress: number
  warnings: { code: string; message: string }[]
  polling: boolean
  failure: Problem | null
  gone: boolean
}

const INITIAL: WatchState = {
  seen: [],
  progress: 0,
  warnings: [],
  polling: false,
  failure: null,
  gone: false,
}

/**
 * `/app/analyses/:id`: the result if it is ready, otherwise the live progress of the real stages.
 * The result is fetched again (never kept anywhere but the in-memory query cache, R-02) when the
 * stream says it is complete.
 */
export function AnalysisPage() {
  const { id = '' } = useParams()
  return <AnalysisBody key={id} id={id} />
}

function AnalysisBody({ id }: { id: string }) {
  const { t, i18n } = useTranslation()
  const language = i18n.language === 'en' ? 'en' : 'bn'
  const location = useLocation()
  const call = useAuthedCall()
  const auth = useAuth()
  const [watch, setWatch] = useState<WatchState>(INITIAL)
  const ocrExpected = (location.state as { ocrExpected?: boolean } | null)?.ocrExpected === true

  const query = useQuery({
    queryKey: ['analysis', id],
    queryFn: () => call((token, lang) => getAnalysis(id, lang, token)),
    retry: false,
    staleTime: Infinity,
    gcTime: 30 * 60 * 1000,
  })
  const data = query.data
  const running = data !== undefined && !isResult(data) && data.status !== 'FAILED'
  useTitle(t(data !== undefined && isResult(data) ? 'results.title' : 'progress.title'))

  // The latest auth functions, so a renewed token does not restart the stream.
  const latest = useRef({ call, auth, language, refetch: query.refetch })
  useEffect(() => {
    latest.current = { call, auth, language, refetch: query.refetch }
  })

  useEffect(() => {
    if (!running) {
      return undefined
    }
    const controller = new AbortController()
    void watchEvents({
      url: `/api/v1/analyses/${id}/events`,
      language: latest.current.language,
      getToken: () => latest.current.auth.getToken(),
      refreshToken: () => latest.current.auth.renewToken(),
      poll: () => latest.current.call((token, lang) => pollAnalysis(id, lang, token)),
      signal: controller.signal,
      pollIntervalMs: watchTiming.pollIntervalMs,
      retryDelayMs: watchTiming.retryDelayMs,
      onEvent: (event) => {
        if (event.name === 'stage') {
          setWatch((current) => ({
            ...current,
            seen: current.seen.includes(event.stage)
              ? current.seen
              : [...current.seen, event.stage],
            progress: Math.max(current.progress, event.progress),
            polling: event.id === 0,
          }))
        } else if (event.name === 'warning') {
          setWatch((current) => ({
            ...current,
            warnings: [...current.warnings, { code: event.code, message: event.message }],
          }))
        }
      },
    }).then((outcome) => {
      if (controller.signal.aborted) {
        return
      }
      if (outcome.kind === 'completed') {
        void latest.current.refetch()
      } else if (outcome.kind === 'failed') {
        setWatch((current) => ({ ...current, failure: outcome.problem }))
      } else if (outcome.kind === 'gone') {
        setWatch((current) => ({ ...current, gone: true }))
      }
    })
    return () => {
      controller.abort()
    }
  }, [running, id])

  if (query.isPending) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 px-(--gutter) py-10" role="status">
        <span className="sr-only">{t('common.loading')}</span>
        <Skeleton className="h-9 w-2/3" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }
  if (query.isError || watch.gone) {
    const gone =
      watch.gone ||
      (query.error instanceof ApiError &&
        (query.error.status === 404 || query.error.status === 410))
    return (
      <div className="mx-auto max-w-2xl px-(--gutter) py-10">
        {gone ? (
          <EmptyState
            icon="clock"
            title={t('progress.expiredTitle')}
            description={t('progress.expiredBody')}
            action={<LinkButton to="/app">{t('progress.again')}</LinkButton>}
          />
        ) : (
          <ErrorState
            error={query.error}
            onRetry={() => {
              void query.refetch()
            }}
          />
        )}
      </div>
    )
  }
  const loaded = query.data
  if (isResult(loaded)) {
    return <ResultsView result={loaded} />
  }
  if (loaded.status === 'FAILED' || watch.failure !== null) {
    const problem = watch.failure ?? loaded.problem
    return (
      <div className="mx-auto max-w-2xl px-(--gutter) py-10">
        <h1 className="mb-4 text-2xl font-bold">{t('progress.failedTitle')}</h1>
        <ErrorState error={new ApiError(problem?.status ?? 500, problem)} />
        <div className="mt-4">
          <LinkButton to="/app">{t('progress.again')}</LinkButton>
        </div>
      </div>
    )
  }
  return (
    <ProgressView
      states={stepStates(watch.seen, { ocrExpected, finished: false })}
      progress={Math.max(watch.progress, loaded.progress)}
      warnings={watch.warnings}
      polling={watch.polling}
    />
  )
}
