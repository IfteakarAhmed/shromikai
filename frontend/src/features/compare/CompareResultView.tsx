import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import type { CompareResult, RiskFinding } from '../../api/analyses'
import { RiskCard } from '../../components/result/RiskCard'
import { Disclaimer } from '../../components/ui/Disclaimer'
import { Tabs } from '../../components/ui/Tabs'
import { sortFindings } from '../../lib/findings'
import { formatNumber } from '../../lib/format'
import type { CitationGroup } from '../../lib/citations'
import { useMediaQuery } from '../landing/useCapabilities'
import { CitationSheet } from '../analysis/CitationSheet'
import { WhySheet } from '../analysis/WhySheet'
import type { Language } from '../../i18n'
import { BaselineColumn } from './BaselineColumn'

/**
 * Compare mode (FR-72): the plain model's answer beside ShromikAI's. The sentence at the top is
 * computed from the counts of the response, never written in advance.
 */
export function CompareResultView({ compare }: { compare: CompareResult }) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const wide = useMediaQuery('(min-width: 768px)')
  const [tab, setTab] = useState('baseline')
  const [citation, setCitation] = useState<CitationGroup | null>(null)
  const [why, setWhy] = useState<RiskFinding | null>(null)
  const { summary, shromikai } = compare
  const n = (value: number) => formatNumber(value, language)
  const scripted = compare.baseline.modelId?.startsWith('scripted-') === true

  const ours = (
    <section aria-labelledby="ours-heading">
      <h2 id="ours-heading" className="text-2xl font-bold">
        {t('compare.oursTitle')}
      </h2>
      <p className="mt-2 text-muted-foreground">
        <Link to={`/app/analyses/${shromikai.analysisId}`} className="underline underline-offset-4">
          {t('results.title')}
        </Link>
      </p>
      <ul className="mt-4 grid gap-4">
        {sortFindings(shromikai.findings).map((finding) => (
          <li key={finding.id}>
            <RiskCard
              finding={finding}
              language={shromikai.outputLanguage}
              onOpenCitation={(group) => {
                setCitation(group)
              }}
              onOpenWhy={(f) => {
                setWhy(f)
              }}
            />
          </li>
        ))}
      </ul>
    </section>
  )
  const baseline = (
    <BaselineColumn baseline={compare.baseline} language={shromikai.outputLanguage} />
  )

  return (
    <div className="mx-auto max-w-6xl px-(--gutter) py-8">
      <h1 className="text-3xl font-bold">{t('compare.title')}</h1>
      {scripted && (
        <p
          role="note"
          className="mt-4 rounded-xl border border-uncertain/50 bg-uncertain/10 px-4 py-3 font-semibold"
        >
          {t('compare.scriptedNotice')}
        </p>
      )}
      <div className="mt-4 rounded-xl border border-rejected/40 bg-rejected/5 p-4" role="status">
        <p className="text-xl font-bold" data-testid="compare-summary">
          {t(scripted ? 'compare.summaryScripted' : 'compare.summary', {
            total: n(summary.baselineCitations),
            rejected: n(summary.baselineRejected),
          })}
        </p>
        <p className="mt-1 text-muted-foreground">
          {t('compare.summaryMore', {
            uncertain: n(summary.baselineUncertain),
            verified: n(summary.baselineVerified),
            shown: n(summary.shromikaiShownCitations),
          })}
        </p>
      </div>

      <div className="mt-6">
        {wide ? (
          <div className="grid grid-cols-2 items-start gap-8">
            {baseline}
            {ours}
          </div>
        ) : (
          <Tabs
            idPrefix="compare"
            label={t('compare.tabsLabel')}
            value={tab}
            onChange={setTab}
            items={[
              { id: 'baseline', label: t('compare.tabBaseline'), panel: baseline },
              { id: 'ours', label: t('compare.tabOurs'), panel: ours },
            ]}
          />
        )}
      </div>

      <div className="mt-8">
        <Disclaimer text={compare.disclaimer} />
      </div>
      <WhySheet
        finding={why}
        result={shromikai}
        onClose={() => {
          setWhy(null)
        }}
      />
      <CitationSheet
        group={citation}
        onClose={() => {
          setCitation(null)
        }}
      />
    </div>
  )
}
