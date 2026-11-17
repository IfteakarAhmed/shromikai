import { cx } from '../../lib/cx'
import { textareaClass } from '../../components/ui/field'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { getCompare, isCompareResult, startCompare, type CompareResult } from '../../api/analyses'
import { useAuthedCall } from '../../api/useAuthedCall'
import { useMeta } from '../../api/useMeta'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Skeleton } from '../../components/ui/Skeleton'
import { formatNumber } from '../../lib/format'
import { stepStates } from '../../lib/stages'
import { MIN_TEXT, validateText } from '../../lib/upload'
import { useTitle } from '../../lib/useTitle'
import { usePreferences } from '../../state/preferencesContext'
import { ProgressView } from '../analysis/ProgressView'
import { useStageWatch } from '../analysis/useStageWatch'
import { CompareResultView } from './CompareResultView'
import type { Language } from '../../i18n'

/** `/app/compare`: start a comparison on the sample or on pasted text. */
export function CompareStartPage() {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  useTitle(t('compare.title'))
  const navigate = useNavigate()
  const call = useAuthedCall()
  const meta = useMeta()
  const { side, locale } = usePreferences()
  const recorded = meta.data?.recordedDemo ?? null
  const [text, setText] = useState('')
  const [invalid, setInvalid] = useState(false)

  const start = useMutation({
    mutationFn: (input: { kind: 'sample' } | { kind: 'text'; text: string }) =>
      call((token, lang) =>
        startCompare(
          input,
          {
            side: recorded === null ? side : (recorded.side as 'WORKER' | 'EMPLOYER'),
            outputLanguage: recorded === null ? locale : (recorded.outputLanguage as 'bn' | 'en'),
          },
          lang,
          token,
        ),
      ),
    onSuccess: (accepted) => {
      void navigate(`/app/compare/${accepted.compareId}`)
    },
  })

  return (
    <div className="mx-auto max-w-5xl px-(--gutter) py-10">
      <h1 className="parallax text-3xl font-bold">{t('compare.title')}</h1>
      <p className="mt-2 text-lg text-muted-foreground">{t('compare.intro')}</p>
      {recorded !== null && (
        <p
          role="note"
          className="mt-4 rounded-lg border border-uncertain/40 bg-uncertain/10 px-4 py-3"
        >
          {t('upload.recordedOnly')}
        </p>
      )}
      <div
        data-testid="compare-start-grid"
        className="mt-6 grid gap-4 lg:grid-cols-2 lg:items-stretch"
      >
        <GlassCard depth="still" className="flex flex-col p-5 sm:p-6">
          <h2 className="text-xl font-bold">{t('compare.sampleTitle')}</h2>
          <p className="mt-1 flex-1 text-muted-foreground">{t('compare.sampleBody')}</p>
          <div className="mt-4">
            <Button
              size="lg"
              busy={start.isPending && start.variables.kind === 'sample'}
              disabled={start.isPending}
              onClick={() => {
                setInvalid(false)
                start.mutate({ kind: 'sample' })
              }}
            >
              {t('compare.start')}
            </Button>
          </div>
        </GlassCard>
        <GlassCard depth="still" className="p-5 sm:p-6">
          <h2 className="text-xl font-bold">{t('compare.textTitle')}</h2>
          <label htmlFor="compare-text" className="sr-only">
            {t('compare.startText')}
          </label>
          <textarea
            id="compare-text"
            rows={8}
            disabled={recorded !== null}
            value={text}
            onChange={(event) => {
              setText(event.target.value)
            }}
            className={cx(textareaClass, 'mt-2')}
          />
          <p className="mt-1 text-sm text-muted-foreground">
            {t('compare.textHint', { min: formatNumber(MIN_TEXT, language) })}
          </p>
          {invalid && (
            <p role="alert" className="mt-2 text-rejected">
              {t('upload.errors.textTooShort', { min: formatNumber(MIN_TEXT, language) })}
            </p>
          )}
          <Button
            className="mt-3"
            variant="secondary"
            disabled={recorded !== null || start.isPending}
            onClick={() => {
              if (validateText(text) !== null) {
                setInvalid(true)
                return
              }
              setInvalid(false)
              start.mutate({ kind: 'text', text: text.trim() })
            }}
          >
            {t('compare.startText')}
          </Button>
        </GlassCard>
      </div>
      {start.isError && (
        <div className="mt-4">
          <ErrorState error={start.error} />
        </div>
      )}
    </div>
  )
}

/** `/app/compare/:id`: progress while both answers are made, then the two side by side. */
export function CompareResultPage() {
  const { id = '' } = useParams()
  return <CompareBody key={id} id={id} />
}

function CompareBody({ id }: { id: string }) {
  const { t, i18n } = useTranslation()
  const language = i18n.language === 'en' ? 'en' : 'bn'
  useTitle(t('compare.title'))
  const call = useAuthedCall()
  const query = useQuery<CompareResult | null>({
    queryKey: ['compare', id],
    queryFn: async () => {
      const body = await call((token, lang) => getCompare(id, lang, token))
      return isCompareResult(body) ? body : null
    },
    retry: false,
    staleTime: Infinity,
    gcTime: 30 * 60 * 1000,
    // The baseline is a model call that follows the analysis: ask again until it is there.
    refetchInterval: (current) => (current.state.data === null ? 1500 : false),
  })
  const waiting = query.isSuccess && query.data === null
  const watch = useStageWatch({
    url: `/api/v1/compare/${id}/events`,
    enabled: waiting,
    language,
    poll: () => Promise.resolve({ state: 'running', stage: null, progress: 0 }),
    onCompleted: () => {
      void query.refetch()
    },
  })

  if (query.isPending) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 px-(--gutter) py-10" role="status">
        <span className="sr-only">{t('common.loading')}</span>
        <Skeleton className="h-9 w-2/3" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }
  if (query.isError) {
    return (
      <div className="mx-auto max-w-2xl px-(--gutter) py-10">
        <ErrorState
          error={query.error}
          onRetry={() => {
            void query.refetch()
          }}
        />
      </div>
    )
  }
  if (query.data === null) {
    return (
      <div>
        <ProgressView
          states={stepStates(watch.seen, { ocrExpected: false, finished: false })}
          progress={watch.progress}
          warnings={watch.warnings}
          polling={watch.polling}
        />
        <p role="status" className="mx-auto max-w-2xl px-(--gutter) text-muted-foreground">
          {t('compare.running')}
        </p>
      </div>
    )
  }
  return <CompareResultView compare={query.data} />
}
