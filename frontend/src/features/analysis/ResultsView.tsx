import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { AnalysisResult, RiskFinding, VerifiedCitation } from '../../api/analyses'
import { Badge } from '../../components/ui/Badge'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon } from '../../components/ui/Icon'
import { LinkButton } from '../../components/ui/Button'
import { Disclaimer } from '../../components/ui/Disclaimer'
import { RiskCard } from '../../components/result/RiskCard'
import { SeverityTag, type Severity } from '../../components/result/SeverityTag'
import { VerificationLegend } from '../../components/result/VerificationLegend'
import { sortFindings } from '../../lib/findings'
import { formatNumber } from '../../lib/format'
import type { Language } from '../../i18n'
import { CitationSheet } from './CitationSheet'

const SEVERITIES: readonly Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

interface ResultsViewProps {
  result: AnalysisResult
}

/**
 * The result: a summary by severity and the uncertain count, one card per risk, the legend of the
 * two verification marks, how it was made and the disclaimer (FR-70, FR-77). Nothing here is
 * computed from anything but the API response.
 */
export function ResultsView({ result }: ResultsViewProps) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const [citation, setCitation] = useState<VerifiedCitation | null>(null)
  const findings = sortFindings(result.findings)
  const { summary } = result
  const n = (value: number) => formatNumber(value, language)

  return (
    <div className="mx-auto max-w-6xl px-(--gutter) pb-28 pt-8 lg:pb-12">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">{t('results.title')}</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {result.input.sample && <Badge tone="neutral">{t('results.sampleLabel')}</Badge>}
            {result.provenance.recorded && <Badge tone="uncertain">{t('common.recorded')}</Badge>}
          </div>
        </div>
        <div className="hidden gap-2 lg:flex">
          <LinkButton to="/app/compare" variant="outline">
            <Icon name="columns" />
            {t('results.compare')}
          </LinkButton>
          <LinkButton to="/app">
            <Icon name="upload" />
            {t('results.newAnalysis')}
          </LinkButton>
        </div>
      </header>

      <section aria-labelledby="summary-heading" className="mt-6">
        <h2 id="summary-heading" className="sr-only">
          {t('results.summaryTitle')}
        </h2>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <GlassCard className="p-4">
            <p className="text-sm text-muted-foreground">{t('results.total')}</p>
            <p className="text-3xl font-bold" data-testid="total-findings">
              {n(result.findings.length)}
            </p>
          </GlassCard>
          <GlassCard className="border-uncertain/40 p-4">
            <p className="text-sm text-muted-foreground">{t('results.uncertain')}</p>
            <p className="text-3xl font-bold text-uncertain" data-testid="uncertain-findings">
              {n(summary.uncertainFindings)}
            </p>
          </GlassCard>
          <GlassCard className="p-4">
            <p className="text-sm text-muted-foreground">{t('results.verifiedCitations')}</p>
            <p className="text-3xl font-bold text-verified">{n(summary.verifiedCitations)}</p>
          </GlassCard>
          <GlassCard className="p-4">
            <p className="text-sm text-muted-foreground">{t('results.nonObvious')}</p>
            <p className="text-3xl font-bold">{n(summary.nonObviousFindings)}</p>
          </GlassCard>
        </div>
        <ul className="mt-3 flex flex-wrap items-center gap-2" aria-label={t('severity.label')}>
          {SEVERITIES.map((severity) => (
            <li key={severity} className="flex items-center gap-1.5">
              <SeverityTag severity={severity} />
              <span className="font-bold">{n(summary.findingsBySeverity[severity])}</span>
            </li>
          ))}
        </ul>
        {summary.overallSeverity === null && (
          <p className="mt-3 text-muted-foreground">{t('results.none')}</p>
        )}
        {summary.coverageNote !== null && (
          <p role="note" className="mt-3 rounded-lg border border-border px-4 py-3">
            <strong>{t('results.coverage')}: </strong>
            {summary.coverageNote}
          </p>
        )}
      </section>

      <div className="mt-6">
        <VerificationLegend />
      </div>

      <section aria-labelledby="findings-heading" className="mt-8">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="findings-heading" className="text-2xl font-bold">
            {t('results.findingsTitle')}
          </h2>
          <p className="text-muted-foreground">
            {t('results.findingsCount', { count: n(findings.length) })} · {t('results.sortNote')}
          </p>
        </div>
        <ul className="mt-4 grid gap-4">
          {findings.map((finding: RiskFinding) => (
            <li key={finding.id}>
              <RiskCard
                finding={finding}
                language={result.outputLanguage}
                onOpenCitation={(c) => {
                  setCitation(c)
                }}
                onOpenWhy={() => undefined}
                ticketHref={
                  result.reviewTicket === null
                    ? undefined
                    : `/app/tickets/${result.reviewTicket.ticketId}`
                }
              />
            </li>
          ))}
        </ul>
      </section>

      {result.warnings.length > 0 && (
        <section aria-labelledby="warnings-heading" className="mt-8">
          <h2 id="warnings-heading" className="text-xl font-bold">
            {t('results.warnings')}
          </h2>
          <ul className="mt-3 space-y-2">
            {result.warnings.map((warning) => (
              <li
                key={warning.code}
                className="rounded-lg border border-uncertain/30 bg-uncertain/5 px-4 py-3"
              >
                {warning.message}
              </li>
            ))}
          </ul>
        </section>
      )}

      <section aria-labelledby="method-heading" className="mt-8">
        <details className="glass-panel rounded-xl p-5">
          <summary
            id="method-heading"
            className="flex min-h-11 cursor-pointer items-center gap-2 text-lg font-bold"
          >
            <Icon name="info" />
            {t('results.method')}
          </summary>
          <dl className="mt-4 grid gap-x-6 gap-y-3 sm:grid-cols-[max-content_1fr]">
            <dt className="text-muted-foreground">{t('results.methodModels')}</dt>
            <dd>
              <ul>
                {Object.entries(result.provenance.models).map(([role, model]) => (
                  <li key={role}>
                    <span className="text-muted-foreground">{role}:</span> {model}
                  </li>
                ))}
              </ul>
            </dd>
            <dt className="text-muted-foreground">{t('results.methodPrompts')}</dt>
            <dd>
              {Object.entries(result.provenance.promptVersions)
                .map(([prompt, version]) => `${prompt} v${version}`)
                .join(', ')}
            </dd>
            <dt className="text-muted-foreground">{t('results.methodCorpus')}</dt>
            <dd>{result.provenance.corpusRelease}</dd>
            <dt className="text-muted-foreground">{t('results.methodIndependence')}</dt>
            <dd>{t(`results.independence.${result.provenance.verifierIndependence}`)}</dd>
          </dl>
          {result.provenance.recorded && (
            <p className="mt-4 text-muted-foreground">
              {t('results.recordedNote', { label: t('common.recorded') })}
            </p>
          )}
        </details>
      </section>

      <div className="mt-8">
        <Disclaimer text={result.disclaimer} />
      </div>

      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-background/95 p-3 backdrop-blur lg:hidden">
        <div className="mx-auto flex max-w-6xl gap-2">
          <LinkButton to="/app/compare" variant="outline" className="flex-1">
            <Icon name="columns" />
            {t('results.compare')}
          </LinkButton>
          <LinkButton to="/app" className="flex-1">
            <Icon name="upload" />
            {t('results.newAnalysis')}
          </LinkButton>
        </div>
      </div>

      <CitationSheet
        citation={citation}
        onClose={() => {
          setCitation(null)
        }}
      />
    </div>
  )
}
