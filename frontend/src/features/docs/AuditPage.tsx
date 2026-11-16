import { useTranslation } from 'react-i18next'
import { useMeta } from '../../api/useMeta'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon } from '../../components/ui/Icon'
import { Skeleton } from '../../components/ui/Skeleton'
import type { Language } from '../../i18n'
import {
  formatDateTime,
  formatDecimal,
  formatNumber,
  formatPercent,
  formatVersion,
} from '../../lib/format'
import { useTitle } from '../../lib/useTitle'
import { ProvenanceNote } from './ProvenanceNote'
import { retrievalReport } from './reports'

const ROLES = [
  'reasoning',
  'fast',
  'verifier',
  'verifierFallback',
  'judge',
  'localPii',
  'embedding',
] as const

/**
 * `/docs/audit` (FR-74): a report read from the running system through `/api/v1/meta`: version,
 * models, corpus, privacy blocks, open reviews and the latest evaluation. Every value is data from
 * the response or from a generated report file; nothing is written into the page by hand (R-07).
 */
export default function AuditPage() {
  const { t, i18n } = useTranslation()
  useTitle(t('docs.nav.audit'))
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const meta = useMeta()
  const retrieval = retrievalReport()
  const n = (value: number) => formatNumber(value, language)
  const best = retrieval?.rows.find((row) => row.method === 'HYBRID_GRAPH') ?? null

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-bold">{t('docs.audit.title')}</h1>
        <Button
          variant="outline"
          onClick={() => {
            window.print()
          }}
        >
          <Icon name="printer" />
          {t('docs.audit.print')}
        </Button>
      </div>
      <p className="mt-2 text-muted-foreground">{t('docs.audit.intro')}</p>

      {meta.isPending && (
        <div role="status" aria-label={t('docs.audit.loading')} className="mt-6 space-y-3">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-48 w-full" />
        </div>
      )}
      {meta.isError && (
        <div className="mt-6">
          <ErrorState
            error={meta.error}
            onRetry={() => {
              void meta.refetch()
            }}
          />
        </div>
      )}
      {meta.data !== undefined && (
        <div className="mt-6 space-y-6">
          {meta.data.recordedDemo !== null && (
            <p
              role="note"
              className="rounded-xl border border-uncertain/50 bg-uncertain/10 px-4 py-3 font-semibold"
            >
              {t('docs.audit.recordedDemo')}
            </p>
          )}

          <section aria-labelledby="audit-models">
            <h2 id="audit-models" className="text-xl font-bold">
              {t('docs.audit.models')}
            </h2>
            <p className="mt-1 text-muted-foreground">
              {t('docs.audit.version')}:{' '}
              <span data-testid="audit-version">{formatVersion(meta.data.version, language)}</span>
            </p>
            <dl className="mt-3 grid gap-3 sm:grid-cols-2">
              {ROLES.map((role) => {
                const model = meta.data.models[role]
                return (
                  <GlassCard key={role} className="px-4 py-3">
                    <dt className="text-sm text-muted-foreground">
                      {t(`docs.audit.modelRoles.${role}`)}
                    </dt>
                    <dd className="mt-1 font-semibold">
                      {model === null ? (
                        t('docs.audit.none')
                      ) : (
                        <code lang="en" dir="ltr" className="break-all">
                          {model}
                        </code>
                      )}
                    </dd>
                  </GlassCard>
                )
              })}
            </dl>
          </section>

          <section aria-labelledby="audit-corpus">
            <h2 id="audit-corpus" className="text-xl font-bold">
              {t('docs.audit.corpus')}
            </h2>
            <dl className="mt-3 grid gap-3 sm:grid-cols-3">
              <GlassCard className="px-4 py-3">
                <dt className="text-sm text-muted-foreground">{t('docs.audit.release')}</dt>
                <dd className="mt-1 font-semibold" data-testid="audit-release">
                  {meta.data.corpus.release === null ? (
                    t('docs.audit.none')
                  ) : (
                    <code lang="en" dir="ltr">
                      {meta.data.corpus.release}
                    </code>
                  )}
                </dd>
              </GlassCard>
              {(
                [
                  ['acts', meta.data.corpus.acts],
                  ['sections', meta.data.corpus.sections],
                  ['currentSections', meta.data.corpus.currentSections],
                  ['embedded', meta.data.corpus.embeddedSections],
                ] as const
              ).map(([key, value]) => (
                <GlassCard key={key} className="px-4 py-3">
                  <dt className="text-sm text-muted-foreground">{t(`docs.audit.${key}`)}</dt>
                  <dd className="mt-1 text-xl font-bold" data-testid={`audit-${key}`}>
                    {n(value)}
                  </dd>
                </GlassCard>
              ))}
              <GlassCard className="px-4 py-3">
                <dt className="text-sm text-muted-foreground">{t('docs.audit.lastRefresh')}</dt>
                <dd className="mt-1 font-semibold">
                  {meta.data.corpus.lastRefreshAt === null
                    ? t('docs.audit.never')
                    : formatDateTime(meta.data.corpus.lastRefreshAt, language)}
                </dd>
              </GlassCard>
            </dl>
          </section>

          <section aria-labelledby="audit-checks">
            <h2 id="audit-checks" className="text-xl font-bold">
              {t('docs.audit.checks')}
            </h2>
            {meta.data.audit === null ? (
              <p className="mt-2 text-muted-foreground">{t('docs.audit.auditOff')}</p>
            ) : (
              <dl className="mt-3 grid gap-3 sm:grid-cols-2">
                <GlassCard className="px-4 py-3">
                  <dt className="text-sm text-muted-foreground">{t('docs.audit.privacyBlocks')}</dt>
                  <dd className="mt-1 text-xl font-bold" data-testid="audit-privacy-blocks">
                    {n(meta.data.audit.privacyBlocksLast7Days)}
                  </dd>
                </GlassCard>
                <GlassCard className="px-4 py-3">
                  <dt className="text-sm text-muted-foreground">{t('docs.audit.openReviews')}</dt>
                  <dd className="mt-1 text-xl font-bold" data-testid="audit-open-reviews">
                    {n(meta.data.audit.openReviewItems)}
                  </dd>
                </GlassCard>
              </dl>
            )}
          </section>

          <section aria-labelledby="audit-eval">
            <h2 id="audit-eval" className="text-xl font-bold">
              {t('docs.audit.evalTitle')}
            </h2>
            {meta.data.audit?.latestEval == null ? (
              <p className="mt-2 text-muted-foreground">{t('docs.audit.noEval')}</p>
            ) : (
              <dl className="mt-3 grid gap-3 sm:grid-cols-3">
                {(
                  [
                    ['evalCases', n(meta.data.audit.latestEval.cases)],
                    ['evalRecall', formatPercent(meta.data.audit.latestEval.riskRecall, language)],
                    [
                      'evalPrecision',
                      formatPercent(meta.data.audit.latestEval.citationPrecision, language),
                    ],
                    ['evalFabricated', n(meta.data.audit.latestEval.fabricatedCitationsShown)],
                    ['evalDate', meta.data.audit.latestEval.reportDate],
                  ] as const
                ).map(([key, value]) => (
                  <GlassCard key={key} className="px-4 py-3">
                    <dt className="text-sm text-muted-foreground">{t(`docs.audit.${key}`)}</dt>
                    <dd className="mt-1 text-xl font-bold">{value}</dd>
                  </GlassCard>
                ))}
              </dl>
            )}
          </section>

          {retrieval !== null && best !== null && (
            <section aria-labelledby="audit-retrieval">
              <h2 id="audit-retrieval" className="text-xl font-bold">
                {t('docs.audit.retrievalTitle')}
              </h2>
              <p className="mt-2 text-lg">
                {t(`docs.evaluation.methods.${best.method}`)}:{' '}
                <strong>
                  {t('docs.evaluation.found', { count: n(best.hitsAt5), n: n(best.questions) })}
                </strong>
                {' · '}
                {formatDecimal(best.mrrAt10, language, 2)}
              </p>
              <ProvenanceNote
                file="eval/reports/retrieval.json"
                provenance={retrieval.provenance}
                language={language}
              />
            </section>
          )}
        </div>
      )}
    </div>
  )
}
