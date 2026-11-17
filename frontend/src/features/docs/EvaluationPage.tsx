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
import { contractEvaluationExists, retrievalReport } from './reports'

const EvalChart = lazy(() => import('./EvalChart'))

/**
 * `/docs/evaluation` (FR-74, R-07): the retrieval numbers from the generated report, each with its
 * count, and an honest "not yet run" for the contract evaluation, which has no report file. No
 * number on this page is written by hand.
 */
export default function EvaluationPage() {
  const { t, i18n } = useTranslation()
  useTitle(t('docs.nav.evaluation'))
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const { lowBandwidth } = usePreferences()
  const retrieval = retrievalReport()
  const n = (value: number) => formatNumber(value, language)
  const contractsRun = contractEvaluationExists()

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
      {contractsRun ? (
        <p className="mt-3">{t('docs.evaluation.contractsFound')}</p>
      ) : (
        <GlassCard className="mt-3 border-uncertain/50 p-5">
          <p className="font-semibold" data-testid="contracts-not-run">
            {t('docs.evaluation.contractsNotRun')}
          </p>
          <p className="mt-2 text-muted-foreground">{t('docs.evaluation.contractsHow')}</p>
        </GlassCard>
      )}
    </div>
  )
}
