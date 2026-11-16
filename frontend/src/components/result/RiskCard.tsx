import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import type { RiskFinding } from '../../api/analyses'
import { groupCitations, type CitationGroup } from '../../lib/citations'
import { localizeDigits } from '../../lib/format'
import type { Language } from '../../i18n'
import { Badge } from '../ui/Badge'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'
import { Icon } from '../ui/Icon'
import { CitationChip } from './CitationChip'
import { SeverityTag } from './SeverityTag'
import { VerificationBadge } from './VerificationBadge'

interface RiskCardProps {
  finding: RiskFinding
  /** The language of the analysis text (for `lang`); the labels follow the interface language. */
  language: Language
  onOpenCitation: (group: CitationGroup, finding: RiskFinding) => void
  onOpenWhy: (finding: RiskFinding) => void
  onOpenProposal?: (finding: RiskFinding) => void
  ticketHref?: string
}

/**
 * One risk. A verified finding shows its citations; an uncertain one shows none and says it was
 * sent for human review (R-01): the card ignores citations unless the finding is `VERIFIED`, so a
 * malformed response cannot put a claim in front of a reader.
 */
export function RiskCard({
  finding,
  language,
  onOpenCitation,
  onOpenWhy,
  onOpenProposal,
  ticketHref,
}: RiskCardProps) {
  const { t, i18n } = useTranslation()
  const uiLanguage: Language = i18n.language === 'en' ? 'en' : 'bn'
  const verified = finding.verificationStatus === 'VERIFIED'
  const uncertain = finding.verificationStatus === 'UNCERTAIN'
  const clauses = finding.clauseIds.map((id) => localizeDigits(id.replace('c', ''), uiLanguage))

  return (
    <GlassCard as="article" className="p-5" aria-labelledby={`finding-${finding.id}`}>
      <div className="flex flex-wrap items-center gap-2">
        <SeverityTag severity={finding.severity} />
        <VerificationBadge status={finding.verificationStatus} />
        {finding.nonObvious && <Badge tone="neutral">{t('finding.nonObvious')}</Badge>}
        <Badge tone="neutral" className="text-muted-foreground">
          {t(`kind.${finding.kind}`)}
        </Badge>
        {finding.source !== 'LLM_ANALYSIS' && (
          <Badge tone="neutral" data-testid="finding-source">
            {t(`finding.source.${finding.source}`)}
          </Badge>
        )}
      </div>

      <div lang={language} className="mt-3">
        <h3 id={`finding-${finding.id}`} className="text-xl font-bold leading-snug">
          {finding.title}
        </h3>
        <p className="mt-2">{finding.explanation}</p>
        {finding.whyItMatters !== '' && (
          <p className="mt-2 text-muted-foreground">
            <strong className="text-foreground">{t('finding.whyItMatters')}: </strong>
            {finding.whyItMatters}
          </p>
        )}
        {finding.sideNote !== null && finding.sideNote !== '' && (
          <p className="mt-2 text-muted-foreground">
            <strong className="text-foreground">{t('finding.forYou')}: </strong>
            {finding.sideNote}
          </p>
        )}
      </div>

      {clauses.length > 0 && (
        <p className="mt-3 text-sm text-muted-foreground">
          {t('finding.clauses')}: {clauses.join(', ')}
        </p>
      )}

      {verified && finding.citations.length > 0 && (
        <div className="mt-4">
          <h4 className="mb-2 text-sm font-semibold text-muted-foreground">
            {t('finding.citations')}
          </h4>
          <div className="flex flex-wrap gap-2">
            {groupCitations(finding.citations).map((group) => (
              <CitationChip
                key={group.sectionKey}
                group={group}
                language={uiLanguage}
                onOpen={(c) => {
                  onOpenCitation(c, finding)
                }}
              />
            ))}
          </div>
        </div>
      )}

      {uncertain && (
        <div className="mt-4 flex items-start gap-2 rounded-lg border border-uncertain/40 bg-uncertain/10 p-3 text-uncertain">
          <Icon name="help" className="mt-1 shrink-0" />
          <p className="font-medium">{t('verification.sentForReview')}</p>
        </div>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <Button
          variant="outline"
          onClick={() => {
            onOpenWhy(finding)
          }}
        >
          <Icon name="help" />
          {t('finding.why')}
        </Button>
        {onOpenProposal !== undefined && finding.redlineId !== null && verified && (
          <Button
            variant="ghost"
            onClick={() => {
              onOpenProposal(finding)
            }}
          >
            {t('finding.seeProposal')}
            <Icon name="arrowRight" />
          </Button>
        )}
        {uncertain && ticketHref !== undefined && (
          <Link
            to={ticketHref}
            className="inline-flex min-h-11 items-center gap-1 rounded-lg px-3 font-semibold text-uncertain underline underline-offset-4"
          >
            {t('finding.ticketLink')}
          </Link>
        )}
      </div>
    </GlassCard>
  )
}
