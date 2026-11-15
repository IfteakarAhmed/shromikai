import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getTrace, type AnalysisResult, type RiskFinding } from '../../api/analyses'
import { useAuthedCall } from '../../api/useAuthedCall'
import { Badge } from '../../components/ui/Badge'
import { Icon } from '../../components/ui/Icon'
import { Sheet } from '../../components/ui/Sheet'
import { Skeleton } from '../../components/ui/Skeleton'
import { formatNumber } from '../../lib/format'
import { groupCitations } from '../../lib/citations'
import { retrievedCount, verdictsFor } from '../../lib/trace'
import type { Language } from '../../i18n'

interface WhySheetProps {
  finding: RiskFinding | null
  result: AnalysisResult
  onClose: () => void
}

function OutcomeMark({ outcome }: { outcome: 'PASS' | 'FAIL' | 'SKIPPED' | 'ERROR' }) {
  const { t } = useTranslation()
  return (
    <Badge tone={outcome === 'PASS' ? 'verified' : outcome === 'FAIL' ? 'rejected' : 'neutral'}>
      {t(`why.outcome.${outcome}`)}
    </Badge>
  )
}

/**
 * The "why" drawer (FR-70): how the risk was found, which sections the search brought and the model
 * claimed, the three checks every shown citation passed, and which models did what. For an
 * uncertain finding it shows which sections were tried and why each failed. Everything comes from
 * the result and its trace; nothing is explained from memory.
 */
export function WhySheet({ finding, result, onClose }: WhySheetProps) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const call = useAuthedCall()
  const trace = useQuery({
    queryKey: ['trace', result.analysisId],
    enabled: finding !== null,
    queryFn: () => call((token, lang) => getTrace(result.analysisId, lang, token)),
    retry: false,
    staleTime: Infinity,
  })
  const n = (value: number) => formatNumber(value, language)

  return (
    <Sheet
      open={finding !== null}
      onClose={onClose}
      title={t('why.title')}
      description={finding?.title}
    >
      {finding !== null && (
        <div className="space-y-6">
          <section>
            <h3 className="font-semibold">{t('why.foundBy')}</h3>
            <p className="text-muted-foreground">{t(`why.source.${finding.source}`)}</p>
          </section>

          <section aria-labelledby="why-retrieved">
            <h3 id="why-retrieved" className="font-semibold">
              {t('why.retrievedTitle')}
            </h3>
            {trace.isPending && (
              <div role="status" aria-label={t('why.loading')} className="mt-2 space-y-2">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-2/3" />
              </div>
            )}
            {trace.isError && (
              <p className="mt-1 text-muted-foreground">{t('why.traceUnavailable')}</p>
            )}
            {trace.isSuccess && (
              <>
                {retrievedCount(trace.data) !== null && (
                  <p className="mt-1 text-muted-foreground">
                    {t('why.retrievedBody', { count: n(retrievedCount(trace.data) ?? 0) })}
                  </p>
                )}
                <h4 className="mt-3 text-sm font-semibold">{t('why.claimedTitle')}</h4>
                <ul className="mt-1 space-y-2">
                  {verdictsFor(trace.data, finding.id).map((verdict) => (
                    <li
                      key={`${verdict.sectionKey}-${verdict.attempt}`}
                      className="rounded-lg border border-border p-3"
                    >
                      <div className="flex flex-wrap items-center gap-2">
                        <code className="text-sm">{verdict.sectionKey}</code>
                        <Badge
                          tone={
                            verdict.status === 'VERIFIED'
                              ? 'verified'
                              : verdict.status === 'UNCERTAIN'
                                ? 'uncertain'
                                : 'rejected'
                          }
                        >
                          {t(`why.verdictStatus.${verdict.status}`)}
                        </Badge>
                        <span className="text-sm text-muted-foreground">
                          {t('why.attempt', { n: n(verdict.attempt) })}
                        </span>
                      </div>
                      {verdict.reasons.length > 0 && (
                        <ul className="mt-1 list-disc ps-5 text-sm text-muted-foreground">
                          {verdict.reasons.map((reason) => (
                            <li key={reason}>
                              {t(`reason.${reason}`, { defaultValue: t('reason.unknown') })}
                            </li>
                          ))}
                        </ul>
                      )}
                    </li>
                  ))}
                  {verdictsFor(trace.data, finding.id).length === 0 && (
                    <li className="text-muted-foreground">{t('why.claimedNone')}</li>
                  )}
                </ul>
              </>
            )}
          </section>

          {finding.verificationStatus === 'UNCERTAIN' && (
            <section
              aria-labelledby="why-uncertain"
              className="rounded-lg border border-uncertain/40 bg-uncertain/10 p-4"
            >
              <h3
                id="why-uncertain"
                className="flex items-center gap-2 font-semibold text-uncertain"
              >
                <Icon name="help" />
                {t('why.uncertainTitle')}
              </h3>
              <p className="mt-1">{t('why.uncertainBody')}</p>
            </section>
          )}

          {finding.verificationStatus === 'VERIFIED' && (
            <section aria-labelledby="why-checks">
              <h3 id="why-checks" className="font-semibold">
                {t('why.checksTitle')}
              </h3>
              <ul className="mt-2 space-y-3">
                {groupCitations(finding.citations).map((group) => {
                  const { checks } = group.primary.verification
                  return (
                    <li key={group.sectionKey} className="rounded-lg border border-border p-3">
                      <code className="text-sm">{group.sectionKey}</code>
                      <dl className="mt-2 space-y-2">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <dt>{t('why.checkExistence')}</dt>
                          <dd>
                            <OutcomeMark outcome={checks.existence.outcome} />
                          </dd>
                        </div>
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <dt>{t('why.checkQuote')}</dt>
                          <dd className="flex items-center gap-2">
                            {checks.quoteMatch.similarity !== null && (
                              <span className="text-sm text-muted-foreground">
                                {t('why.similarity', {
                                  value: formatNumber(checks.quoteMatch.similarity, language).slice(
                                    0,
                                    6,
                                  ),
                                })}
                              </span>
                            )}
                            <OutcomeMark outcome={checks.quoteMatch.outcome} />
                          </dd>
                        </div>
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <dt>{t('why.checkSupport')}</dt>
                          <dd className="flex items-center gap-2">
                            {checks.support.label !== null && (
                              <span className="text-sm text-muted-foreground">
                                {t(`why.support.${checks.support.label}`)}
                              </span>
                            )}
                            <OutcomeMark outcome={checks.support.outcome} />
                          </dd>
                        </div>
                      </dl>
                      {checks.support.modelId !== null && (
                        <p className="mt-2 text-sm text-muted-foreground">
                          {t('why.verifiedBy', { model: checks.support.modelId })}
                          {checks.support.independence !== null &&
                            ` · ${checks.support.independence}`}
                        </p>
                      )}
                    </li>
                  )
                })}
              </ul>
            </section>
          )}

          <section aria-labelledby="why-models">
            <h3 id="why-models" className="font-semibold">
              {t('why.modelsTitle')}
            </h3>
            <ul className="mt-1 text-sm text-muted-foreground">
              {Object.entries(result.provenance.models).map(([role, model]) => (
                <li key={role}>
                  {role}: {model}
                </li>
              ))}
            </ul>
            <p className="mt-1 text-sm text-muted-foreground">
              {t(`results.independence.${result.provenance.verifierIndependence}`)}
            </p>
            {result.provenance.recorded && (
              <p className="mt-1 text-sm text-muted-foreground">
                {t('results.recordedNote', { label: t('common.recorded') })}
              </p>
            )}
          </section>
        </div>
      )}
    </Sheet>
  )
}
