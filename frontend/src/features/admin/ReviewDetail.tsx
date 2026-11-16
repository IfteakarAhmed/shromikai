import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import {
  getReviewItem,
  patchReviewItem,
  type ReviewItem,
  type ReviewItemPatch,
} from '../../api/admin'
import { useAuthedCall } from '../../api/useAuthedCall'
import { SeverityTag } from '../../components/result/SeverityTag'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Skeleton } from '../../components/ui/Skeleton'
import { formatDateTime, formatDecimal } from '../../lib/format'
import { ReviewStatusBadge } from './ReviewStatusBadge'
import { useItemTitle } from './useItemTitle'
import type { Language } from '../../i18n'

type Decision = NonNullable<ReviewItemPatch['decision']>
const DECISIONS: readonly Decision[] = [
  'APPROVED_WITH_CITATION',
  'APPROVED_WITHOUT_CITATION',
  'REJECTED',
]

/** The open item (E-24): what the verifier saw, and the controls to claim and decide it. */
export function ReviewDetail({ id, language }: { id: string; language: Language }) {
  const { t } = useTranslation()
  const call = useAuthedCall()
  const client = useQueryClient()
  const title = useItemTitle()
  const [notice, setNotice] = useState<string | null>(null)
  const query = useQuery({
    queryKey: ['admin', 'review-item', id],
    queryFn: () => call((token, lang) => getReviewItem(id, lang, token)),
    gcTime: 5 * 60 * 1000,
  })
  const patch = useMutation({
    mutationFn: (body: ReviewItemPatch) =>
      call((token, lang) => patchReviewItem(id, body, lang, token)),
    onSuccess: (item, body) => {
      client.setQueryData(['admin', 'review-item', id], item)
      void client.invalidateQueries({ queryKey: ['admin', 'review', 'list'] })
      void client.invalidateQueries({ queryKey: ['admin', 'review', 'open-count'] })
      setNotice(
        body.decision !== undefined
          ? t('admin.review.decide.saved')
          : body.status === 'IN_REVIEW'
            ? t('admin.review.decide.claimed')
            : t('admin.review.decide.released'),
      )
    },
    onMutate: () => {
      setNotice(null)
    },
  })

  return (
    <section aria-labelledby="review-detail-heading">
      <Link
        to="/admin/review"
        className="mb-3 inline-flex min-h-11 items-center font-semibold underline underline-offset-4 lg:hidden"
      >
        {t('admin.review.back')}
      </Link>
      {query.isPending && (
        <div role="status" aria-label={t('common.loading')} className="space-y-3">
          <Skeleton className="h-10 w-2/3" />
          <Skeleton className="h-40 w-full" />
        </div>
      )}
      {query.isError && (
        <ErrorState
          error={query.error}
          onRetry={() => {
            void query.refetch()
          }}
        />
      )}
      {query.data !== undefined && (
        <GlassCard depth="still" className="space-y-5 p-5">
          <header>
            <h3 id="review-detail-heading" className="text-xl font-bold">
              {title(query.data)}
            </h3>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <ReviewStatusBadge status={query.data.status} />
              {query.data.severity !== null && <SeverityTag severity={query.data.severity} />}
              {query.data.findingKind !== null && (
                <span className="text-muted-foreground">{t(`kind.${query.data.findingKind}`)}</span>
              )}
            </div>
          </header>
          <Body item={query.data} language={language} />
          {notice !== null && (
            <p role="status" className="font-semibold text-verified">
              {notice}
            </p>
          )}
          {patch.isError && <ErrorState error={patch.error} />}
          <Actions
            item={query.data}
            busy={patch.isPending}
            language={language}
            onPatch={(body) => {
              patch.mutate(body)
            }}
          />
        </GlassCard>
      )}
    </section>
  )
}

function Body({ item, language }: { item: ReviewItem; language: Language }) {
  const { t } = useTranslation()
  const claimed = item.claimedCitation
  return (
    <>
      <div>
        <h4 className="font-semibold">{t('admin.review.summary')}</h4>
        <p className="mt-1">{item.riskSummary ?? t('admin.review.noSummary')}</p>
      </div>
      <div>
        <h4 className="font-semibold">{t('admin.review.reasons')}</h4>
        {item.reasonCodes.length === 0 ? (
          <p className="mt-1 text-muted-foreground">{t('admin.review.noReasons')}</p>
        ) : (
          <ul className="mt-1 list-disc ps-5">
            {item.reasonCodes.map((code) => (
              <li key={code}>
                {code === 'RULE_LLM_CONFLICT'
                  ? t('admin.review.ruleConflict')
                  : t(`reason.${code}`, { defaultValue: t('reason.unknown') })}
              </li>
            ))}
          </ul>
        )}
      </div>
      <div>
        <h4 className="font-semibold">{t('admin.review.facts')}</h4>
        {item.facts.length === 0 ? (
          <p className="mt-1 text-muted-foreground">{t('admin.review.noFacts')}</p>
        ) : (
          <ul className="mt-1 space-y-1">
            {item.facts.map((fact) => (
              <li key={`${fact.parameter}-${String(fact.value)}`}>
                <span className="font-medium">{t(`fact.${fact.parameter}`)}</span>
                {': '}
                {fact.value === null ? '–' : formatDecimal(fact.value, language)}{' '}
                {t(`unit.${fact.unit}`)}
                <span className="text-sm text-muted-foreground">
                  {' · '}
                  {t('admin.review.confidence', {
                    value: formatDecimal(fact.confidence, language),
                  })}
                  {fact.regexAgrees === null
                    ? ''
                    : ` · ${t(fact.regexAgrees ? 'admin.review.regexAgrees' : 'admin.review.regexDisagrees')}`}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div>
        <h4 className="font-semibold">{t('admin.review.claimed')}</h4>
        {claimed === null ? (
          <p className="mt-1 text-muted-foreground">{t('admin.review.noClaim')}</p>
        ) : (
          <dl className="mt-1 space-y-2">
            <div>
              <dt className="text-sm text-muted-foreground">{t('admin.review.sectionKey')}</dt>
              <dd>
                <code lang="en" dir="ltr" className="break-all">
                  {claimed.sectionKey}
                </code>
              </dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">{t('admin.review.quote')}</dt>
              <dd>{claimed.quote}</dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">{t('admin.review.claim')}</dt>
              <dd>{claimed.claim}</dd>
            </div>
            {claimed.claimReduced && (
              <p className="text-sm text-muted-foreground">{t('admin.review.claimReduced')}</p>
            )}
          </dl>
        )}
      </div>
      {item.candidateSectionKeys.length > 0 && (
        <div>
          <h4 className="font-semibold">{t('admin.review.candidates')}</h4>
          <ul className="mt-1 flex flex-wrap gap-2">
            {item.candidateSectionKeys.map((key) => (
              <li key={key}>
                <code
                  lang="en"
                  dir="ltr"
                  className="rounded-md border border-border bg-secondary px-2 py-0.5 text-sm"
                >
                  {key}
                </code>
              </li>
            ))}
          </ul>
        </div>
      )}
      {item.corpusChange !== null && (
        <div>
          <h4 className="font-semibold">{t('admin.review.corpus')}</h4>
          <dl className="mt-1 space-y-2">
            <div>
              <dt className="text-sm text-muted-foreground">{t('admin.review.storedHash')}</dt>
              <dd>
                <code lang="en" dir="ltr" className="break-all text-sm">
                  {item.corpusChange.storedSha256}
                </code>
              </dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">{t('admin.review.newHash')}</dt>
              <dd>
                <code lang="en" dir="ltr" className="break-all text-sm">
                  {item.corpusChange.newSha256}
                </code>
              </dd>
            </div>
          </dl>
        </div>
      )}
      {item.decision !== null && (
        <div>
          <h4 className="font-semibold">{t(`admin.review.decide.${item.decision.decision}`)}</h4>
          <p className="mt-1 text-muted-foreground">
            {t('admin.review.decided', { when: formatDateTime(item.decision.decidedAt, language) })}
          </p>
          {item.decision.note !== null && (
            <p className="mt-1">{t('admin.review.decidedNote', { note: item.decision.note })}</p>
          )}
        </div>
      )}
    </>
  )
}

function Actions({
  item,
  busy,
  onPatch,
  language,
}: {
  item: ReviewItem
  busy: boolean
  onPatch: (body: ReviewItemPatch) => void
  language: Language
}) {
  const { t } = useTranslation()
  if (item.status === 'OPEN') {
    return (
      <div className="space-y-2">
        <p className="text-muted-foreground">{t('admin.review.claimFirst')}</p>
        <Button
          busy={busy}
          onClick={() => {
            onPatch({ status: 'IN_REVIEW' })
          }}
        >
          {t('admin.review.claimButton')}
        </Button>
      </div>
    )
  }
  if (item.status !== 'IN_REVIEW') {
    return null
  }
  return (
    <div className="space-y-4">
      <DecisionForm item={item} busy={busy} onSubmit={onPatch} language={language} />
      <Button
        variant="outline"
        disabled={busy}
        onClick={() => {
          onPatch({ status: 'OPEN' })
        }}
      >
        {t('admin.review.release')}
      </Button>
    </div>
  )
}

interface FormErrors {
  decision?: string
  sectionKey?: string
  quote?: string
  claim?: string
  note?: string
}

/**
 * The decision (E-25). A note is always required: it is what the audit trail keeps (FR-60). With a
 * citation the section key, the quote and the claim are needed too; the server verifies them again.
 */
function DecisionForm({
  item,
  busy,
  onSubmit,
  language,
}: {
  item: ReviewItem
  busy: boolean
  onSubmit: (body: ReviewItemPatch) => void
  language: Language
}) {
  const { t } = useTranslation()
  const formRef = useRef<HTMLFormElement>(null)
  const claimed = item.claimedCitation
  const [decision, setDecision] = useState<Decision | ''>('')
  const [sectionKey, setSectionKey] = useState(claimed?.sectionKey ?? '')
  const [quote, setQuote] = useState(claimed?.quote ?? '')
  const [claim, setClaim] = useState(claimed?.claim ?? '')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<FormErrors>({})

  function validate(): FormErrors {
    const found: FormErrors = {}
    if (decision === '') {
      found.decision = t('admin.review.decide.required')
    }
    if (note.trim() === '') {
      found.note = t('admin.review.decide.noteRequired')
    }
    if (decision === 'APPROVED_WITH_CITATION') {
      if (sectionKey.trim() === '') {
        found.sectionKey = t('admin.review.decide.required')
      }
      if (quote.trim().length < 20) {
        found.quote = t('admin.review.decide.tooShort', { min: formatDecimal(20, language, 0) })
      }
      if (claim.trim().length < 20) {
        found.claim = t('admin.review.decide.tooShort', { min: formatDecimal(20, language, 0) })
      }
    }
    return found
  }

  function submit(event: SyntheticEvent) {
    event.preventDefault()
    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0) {
      window.setTimeout(() => {
        formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
      }, 0)
      return
    }
    if (decision === '') {
      return
    }
    onSubmit(
      decision === 'APPROVED_WITH_CITATION'
        ? {
            decision,
            sectionKey: sectionKey.trim(),
            quote: quote.trim(),
            claim: claim.trim(),
            note: note.trim(),
          }
        : { decision, note: note.trim() },
    )
  }

  const field =
    'mt-1 block min-h-11 w-full rounded-lg border border-input bg-background/60 px-3 py-2'
  return (
    <form ref={formRef} onSubmit={submit} noValidate className="space-y-4">
      <h4 className="text-lg font-bold">{t('admin.review.decide.title')}</h4>
      <fieldset aria-describedby={errors.decision === undefined ? undefined : 'decision-error'}>
        <legend className="font-semibold">{t('admin.review.decide.label')}</legend>
        <div className="mt-1 space-y-1">
          {DECISIONS.map((value) => (
            <label key={value} className="flex min-h-11 items-center gap-3">
              <input
                type="radio"
                name="decision"
                value={value}
                checked={decision === value}
                aria-invalid={errors.decision === undefined ? undefined : true}
                onChange={() => {
                  setDecision(value)
                }}
                className="size-5"
              />
              {t(`admin.review.decide.${value}`)}
            </label>
          ))}
        </div>
        {errors.decision !== undefined && (
          <p id="decision-error" className="mt-1 font-semibold text-rejected">
            {errors.decision}
          </p>
        )}
      </fieldset>

      {decision === 'APPROVED_WITH_CITATION' && (
        <>
          <div>
            <label htmlFor="decide-section" className="font-semibold">
              {t('admin.review.decide.sectionKey')}
            </label>
            <input
              id="decide-section"
              list="decide-section-options"
              value={sectionKey}
              dir="ltr"
              lang="en"
              autoComplete="off"
              aria-invalid={errors.sectionKey === undefined ? undefined : true}
              aria-describedby="decide-section-help"
              onChange={(event) => {
                setSectionKey(event.target.value)
              }}
              className={field}
            />
            <datalist id="decide-section-options">
              {item.candidateSectionKeys.map((key) => (
                <option key={key} value={key} />
              ))}
            </datalist>
            <p id="decide-section-help" className="mt-1 text-sm text-muted-foreground">
              {t('admin.review.decide.sectionKeyHelp')}
            </p>
            {errors.sectionKey !== undefined && (
              <p className="mt-1 font-semibold text-rejected">{errors.sectionKey}</p>
            )}
          </div>
          <div>
            <label htmlFor="decide-quote" className="font-semibold">
              {t('admin.review.decide.quote')}
            </label>
            <textarea
              id="decide-quote"
              rows={3}
              maxLength={300}
              value={quote}
              aria-invalid={errors.quote === undefined ? undefined : true}
              onChange={(event) => {
                setQuote(event.target.value)
              }}
              className={field}
            />
            {errors.quote !== undefined && (
              <p className="mt-1 font-semibold text-rejected">{errors.quote}</p>
            )}
          </div>
          <div>
            <label htmlFor="decide-claim" className="font-semibold">
              {t('admin.review.decide.claim')}
            </label>
            <textarea
              id="decide-claim"
              rows={3}
              maxLength={600}
              value={claim}
              aria-invalid={errors.claim === undefined ? undefined : true}
              onChange={(event) => {
                setClaim(event.target.value)
              }}
              className={field}
            />
            {errors.claim !== undefined && (
              <p className="mt-1 font-semibold text-rejected">{errors.claim}</p>
            )}
          </div>
        </>
      )}

      <div>
        <label htmlFor="decide-note" className="font-semibold">
          {t('admin.review.decide.note')}
        </label>
        <textarea
          id="decide-note"
          rows={3}
          maxLength={1000}
          value={note}
          required
          aria-required="true"
          aria-invalid={errors.note === undefined ? undefined : true}
          aria-describedby={
            errors.note === undefined ? 'decide-note-help' : 'decide-note-help decide-note-error'
          }
          onChange={(event) => {
            setNote(event.target.value)
          }}
          className={field}
        />
        <p id="decide-note-help" className="mt-1 text-sm text-muted-foreground">
          {t('admin.review.decide.noteHelp')}
        </p>
        {errors.note !== undefined && (
          <p id="decide-note-error" className="mt-1 font-semibold text-rejected">
            {errors.note}
          </p>
        )}
      </div>
      <Button type="submit" busy={busy}>
        {t('admin.review.decide.submit')}
      </Button>
    </form>
  )
}
