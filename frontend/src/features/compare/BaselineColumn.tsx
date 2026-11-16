import { useTranslation } from 'react-i18next'
import type { CompareResult } from '../../api/analyses'
import { ClaimStatusBadge } from '../../components/result/ClaimStatusBadge'
import { Badge } from '../../components/ui/Badge'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon } from '../../components/ui/Icon'
import { cx } from '../../lib/cx'

type Baseline = CompareResult['baseline']

/**
 * The plain model's answer in compare mode. Every claimed section is shown as a labelled claim with
 * the verifier's verdict and reasons, never as a citation: there is no chip, no link and no section
 * text here (R-01, ADR-0022).
 */
export function BaselineColumn({ baseline, language }: { baseline: Baseline; language: string }) {
  const { t } = useTranslation()
  const scripted = baseline.modelId?.startsWith('scripted-') === true
  return (
    <section aria-labelledby="baseline-heading">
      <h2 id="baseline-heading" className="text-2xl font-bold">
        {t('compare.baselineTitle')}
      </h2>
      <p
        role="note"
        className="mt-2 rounded-lg border border-uncertain/30 bg-uncertain/5 px-4 py-3"
      >
        {t('compare.baselineNote')}
      </p>
      {baseline.modelId !== null && !scripted && (
        <p className="mt-2 text-sm text-muted-foreground">
          {t('compare.model', { model: baseline.modelId })}
        </p>
      )}
      {scripted && <p className="mt-1 text-sm text-muted-foreground">{t('compare.scripted')}</p>}

      {baseline.status === 'UNAVAILABLE' && (
        <p role="status" className="mt-4 text-muted-foreground">
          {t('compare.unavailable')}
        </p>
      )}
      {baseline.status === 'OK' && baseline.findings.length === 0 && (
        <p className="mt-4 text-muted-foreground">{t('compare.noFindings')}</p>
      )}
      <ul className="mt-4 grid gap-4">
        {baseline.findings.map((finding, index) => (
          <li key={`${finding.title}-${index}`}>
            <GlassCard as="article" className="p-4">
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone="neutral">
                  {t(`severity.${finding.severity.toLowerCase()}`, {
                    defaultValue: finding.severity,
                  })}
                </Badge>
              </div>
              <div lang={language}>
                <h3 className="mt-2 text-lg font-bold">{finding.title}</h3>
                <p className="mt-1">{finding.explanation}</p>
              </div>
              <ul className="mt-3 grid gap-3">
                {finding.citations.map((claim, claimIndex) => (
                  <li
                    key={`${claim.claimed.sectionKey}-${claimIndex}`}
                    className={cx(
                      'rounded-lg border p-3',
                      claim.verification.status === 'REJECTED'
                        ? 'border-rejected/50 bg-rejected/5'
                        : claim.verification.status === 'UNCERTAIN'
                          ? 'border-uncertain/50 bg-uncertain/5'
                          : 'border-border',
                    )}
                  >
                    <div className="flex flex-wrap items-center gap-2">
                      <ClaimStatusBadge status={claim.verification.status} />
                      <span className="text-sm text-muted-foreground">
                        {t('compare.notACitation')}
                      </span>
                    </div>
                    <p className="mt-2 text-sm text-muted-foreground">
                      {t('compare.claimedSection')}
                    </p>
                    <code className="text-sm">{claim.claimed.sectionKey}</code>
                    <p className="mt-2 text-sm text-muted-foreground">
                      {t('compare.claimedQuote')}
                    </p>
                    <p lang={language} className="italic">
                      {claim.claimed.quote}
                    </p>
                    {claim.verification.reasonCodes.length > 0 && (
                      <ul className="mt-2 space-y-1">
                        {claim.verification.reasonCodes.map((code) => (
                          <li key={code} className="flex items-start gap-1.5 text-sm">
                            <Icon name="x" size={16} className="mt-1 shrink-0 text-rejected" />
                            {t(`reason.${code}`, { defaultValue: t('reason.unknown') })}
                          </li>
                        ))}
                      </ul>
                    )}
                  </li>
                ))}
              </ul>
            </GlassCard>
          </li>
        ))}
      </ul>
    </section>
  )
}
