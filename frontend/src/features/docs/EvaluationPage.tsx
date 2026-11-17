import { TableShell } from '../../components/ui/TableShell'
import { Suspense, lazy } from 'react'
import { useTranslation } from 'react-i18next'
import { GlassCard } from '../../components/ui/GlassCard'
import { Skeleton } from '../../components/ui/Skeleton'
import type { Language } from '../../i18n'
import { formatDecimal, formatNumber } from '../../lib/format'
import { useTitle } from '../../lib/useTitle'
import { usePreferences } from '../../state/preferencesContext'
import { ProvenanceNote } from './ProvenanceNote'
import { contractReport, retrievalReport } from './reports'

const EvalChart = lazy(() => import('./EvalChart'))

/**
 * `/docs/evaluation` (FR-74, R-07): the retrieval numbers and the contract evaluation from the
 * generated report files, each with its count, the sample size, the number of reviewed labels,
 * what was not measured and the limitations. Without a report file the page says "not yet run".
 * No number on this page is written by hand; the report's own wording is shown in English.
 */
export default function EvaluationPage() {
  const { t, i18n } = useTranslation()
  useTitle(t('docs.nav.evaluation'))
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const { lowBandwidth } = usePreferences()
  const retrieval = retrievalReport()
  const n = (value: number) => formatNumber(value, language)
  const contracts = contractReport()

  return (
    <div>
      <h1 className="text-3xl font-bold">{t('docs.evaluation.title')}</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">{t('docs.evaluation.intro')}</p>

      <h2 className="mt-8 text-2xl font-bold">{t('docs.evaluation.retrievalTitle')}</h2>
      {retrieval === null ? (
        <p className="mt-2">{t('docs.evaluation.noRetrieval')}</p>
      ) : (
        <>
          <p className="mt-2 text-muted-foreground">
            {t('docs.evaluation.retrievalIntro', { questions: n(retrieval.questions) })}
          </p>
          <ProvenanceNote
            file="eval/reports/retrieval.json"
            provenance={retrieval.provenance}
            language={language}
          />
          <TableShell label={t('docs.evaluation.caption')} minWidth="34rem" className="mt-4">
            <caption className="sr-only">{t('docs.evaluation.caption')}</caption>
            <thead>
              <tr className="border-b border-border">
                <th scope="col" className="px-3 py-2 text-start">
                  {t('docs.evaluation.colMethod')}
                </th>
                <th scope="col" className="px-3 py-2 text-end">
                  {t('docs.evaluation.colFound')}
                </th>
                <th scope="col" className="px-3 py-2 text-end">
                  {t('docs.evaluation.colRank')}
                </th>
                <th scope="col" className="px-3 py-2 text-end">
                  {t('docs.evaluation.colRecall')}
                </th>
                <th scope="col" className="px-3 py-2 text-end">
                  {t('docs.evaluation.colTime')}
                </th>
              </tr>
            </thead>
            <tbody>
              {retrieval.rows.map((row) => (
                <tr key={row.method} className="border-b border-border last:border-b-0">
                  <th scope="row" className="px-3 py-2 text-start font-medium">
                    {t(`docs.evaluation.methods.${row.method}`, { defaultValue: row.method })}
                  </th>
                  <td className="px-3 py-2 text-end">
                    {t('docs.evaluation.found', { count: n(row.hitsAt5), n: n(row.questions) })}
                  </td>
                  <td className="px-3 py-2 text-end">{formatDecimal(row.mrrAt10, language, 2)}</td>
                  <td className="px-3 py-2 text-end">
                    {formatDecimal(row.recallAt8, language, 2)}
                  </td>
                  <td className="px-3 py-2 text-end">
                    {t('docs.evaluation.ms', {
                      value: formatDecimal(row.meanMillis, language, 0),
                    })}
                  </td>
                </tr>
              ))}
              {retrieval.skipped.map((row) => (
                <tr key={row.method} className="border-b border-border last:border-b-0">
                  <th scope="row" className="px-3 py-2 text-start font-medium">
                    {t(`docs.evaluation.methods.${row.method}`, { defaultValue: row.method })}
                  </th>
                  <td colSpan={4} className="px-3 py-2">
                    <strong>{t('docs.evaluation.notRun')}</strong>
                    <span className="text-muted-foreground">
                      {' · '}
                      {t('docs.evaluation.notRunReason')}:{' '}
                    </span>
                    <span lang="en">{row.reason}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </TableShell>
          <div className="mt-6">
            {lowBandwidth ? (
              <p className="rounded-lg border border-border bg-secondary/60 p-3">
                {t('docs.evaluation.chartLowBandwidth')}
              </p>
            ) : (
              <Suspense fallback={<Skeleton className="h-80 w-full" />}>
                <EvalChart
                  language={language}
                  data={retrieval.rows.map((row) => ({
                    method: row.method,
                    found: row.hitsAt5,
                    questions: row.questions,
                  }))}
                />
              </Suspense>
            )}
          </div>
        </>
      )}

      <h2 className="mt-10 text-2xl font-bold">{t('docs.evaluation.contractsTitle')}</h2>
      {contracts === null ? (
        <GlassCard className="mt-3 border-uncertain/50 p-5">
          <p className="font-semibold" data-testid="contracts-not-run">
            {t('docs.evaluation.contractsNotRun')}
          </p>
          <p className="mt-2 text-muted-foreground">{t('docs.evaluation.contractsHow')}</p>
        </GlassCard>
      ) : (
        <>
          <p className="mt-2 text-muted-foreground" data-testid="contracts-sample">
            {t('docs.evaluation.contractsSample', {
              contracts: n(contracts.contracts),
              reviewed: n(contracts.reviewedLabelFiles),
              labels: n(contracts.labelFiles),
              cases: n(contracts.adversarialCases),
              spend: formatDecimal(contracts.liveSpendMicros / 1_000_000, language, 2),
            })}
          </p>
          <ProvenanceNote
            file="eval/reports/report.json"
            provenance={contracts.provenance}
            language={language}
          />
          <p className="mt-4 text-sm text-muted-foreground">
            {t('docs.evaluation.contractsEnglish')}
          </p>
          <div data-testid="contracts-headline">
            <TableShell
              label={t('docs.evaluation.headlineCaption')}
              minWidth="40rem"
              className="mt-2"
            >
              <caption className="sr-only">{t('docs.evaluation.headlineCaption')}</caption>
              <thead>
                <tr className="border-b border-border">
                  <th scope="col" className="px-3 py-2 text-start">
                    {t('docs.evaluation.colMetric')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-start">
                    {t('docs.evaluation.colBaseline')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-start">
                    {t('docs.evaluation.colShromikai')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-start">
                    {t('docs.evaluation.colTarget')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-start">
                    {t('docs.evaluation.colMet')}
                  </th>
                </tr>
              </thead>
              <tbody lang="en">
                {contracts.headline.map((row) => (
                  <tr key={row.id} className="border-b border-border last:border-b-0">
                    <th scope="row" className="px-3 py-2 text-start font-medium">
                      {row.id} {row.metric}
                    </th>
                    <td className="px-3 py-2">{row.baseline}</td>
                    <td className="px-3 py-2">{row.shromikai}</td>
                    <td className="px-3 py-2">{row.target}</td>
                    <td className="px-3 py-2" lang={language}>
                      {row.met === null
                        ? t('docs.evaluation.metNone')
                        : row.met
                          ? t('docs.evaluation.metYes')
                          : t('docs.evaluation.metNo')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </TableShell>
          </div>
          <h3 className="mt-6 text-xl font-bold">{t('docs.evaluation.unmeasuredTitle')}</h3>
          <ul className="mt-2 list-disc space-y-1 ps-6" data-testid="contracts-unmeasured">
            {contracts.unmeasured.map((m) => (
              <li key={m.id} lang="en">
                <strong>
                  {m.id} {m.name}
                </strong>
                {' · '}
                {m.status.replace('_', ' ').toLowerCase()}: {m.note}
              </li>
            ))}
          </ul>
          <h3 className="mt-6 text-xl font-bold">{t('docs.evaluation.limitationsTitle')}</h3>
          <ul className="mt-2 list-disc space-y-1 ps-6">
            {contracts.limitations.map((l) => (
              <li key={l} lang="en">
                {l}
              </li>
            ))}
          </ul>
          <h3 className="mt-6 text-xl font-bold">{t('docs.evaluation.knownFailuresTitle')}</h3>
          <ul className="mt-2 list-disc space-y-1 ps-6">
            {contracts.knownFailures.map((l) => (
              <li key={l} lang="en">
                {l}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
