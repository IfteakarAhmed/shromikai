import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { AnalysisResult, Redline } from '../../api/analyses'
import { CitationChip } from '../../components/result/CitationChip'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon } from '../../components/ui/Icon'
import { differentLanguages, scriptOf } from '../../lib/script'
import { groupCitations, type CitationGroup } from '../../lib/citations'
import type { Language } from '../../i18n'

interface RedlineViewProps {
  result: AnalysisResult
  onOpenCitation: (group: CitationGroup) => void
}

/** The redline: what the counter-offer agent proposes for each verified risk, word by word. */
export function RedlineView({ result, onOpenCitation }: RedlineViewProps) {
  const { t, i18n } = useTranslation()
  const uiLanguage: Language = i18n.language === 'en' ? 'en' : 'bn'
  const titles = new Map(result.findings.map((finding) => [finding.id, finding.title]))
  const unchecked = result.warnings.find((warning) => warning.code === 'counter-offer-unchecked')

  if (result.redlines.length === 0) {
    return <EmptyState icon="file" title={t('redline.none')} description={t('redline.noneBody')} />
  }
  return (
    <div>
      <h2 className="text-2xl font-bold">{t('redline.title')}</h2>
      <p className="mt-1 text-muted-foreground">{t('redline.intro')}</p>
      {unchecked !== undefined && (
        <p
          role="note"
          className="mt-3 rounded-lg border border-uncertain/30 bg-uncertain/5 px-4 py-3"
        >
          {unchecked.message}
        </p>
      )}
      <ul className="mt-5 grid gap-4">
        {result.redlines.map((redline) => (
          <li key={redline.id}>
            <RedlineCard
              redline={redline}
              title={titles.get(redline.findingIds[0] ?? '') ?? ''}
              uiLanguage={uiLanguage}
              onOpenCitation={onOpenCitation}
            />
          </li>
        ))}
      </ul>
    </div>
  )
}

function RedlineCard({
  redline,
  title,
  uiLanguage,
  onOpenCitation,
}: {
  redline: Redline
  title: string
  uiLanguage: Language
  onOpenCitation: (group: CitationGroup) => void
}) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const proposed = redline.proposedText
  const sideBySide = differentLanguages(redline.originalText, proposed)

  return (
    <GlassCard as="article" className="p-5" aria-labelledby={`redline-${redline.id}`}>
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="neutral">{t(`redline.action.${redline.action}`)}</Badge>
        {redline.ruleCheck.ruleIds.length > 0 && (
          <Badge tone="verified">{t('redline.ruleChecked')}</Badge>
        )}
      </div>
      <h3 id={`redline-${redline.id}`} className="mt-2 text-lg font-bold">
        {t('redline.forFinding', { title })}
      </h3>

      {sideBySide ? (
        <div className="mt-3">
          <p className="text-sm text-muted-foreground">{t('redline.differentLanguages')}</p>
          <div className="mt-2 grid gap-3 md:grid-cols-2">
            <div
              role="group"
              aria-label={t('redline.original')}
              className="rounded-lg border border-border bg-background/50 p-4"
            >
              <h4 className="text-sm font-semibold text-muted-foreground">
                {t('redline.original')}
              </h4>
              <p
                lang={scriptOf(redline.originalText ?? '') ?? 'en'}
                className="mt-1 whitespace-pre-line leading-relaxed"
              >
                {redline.originalText}
              </p>
            </div>
            <div
              role="group"
              aria-label={t('redline.proposed')}
              className="rounded-lg border border-verified/40 bg-verified/5 p-4"
            >
              <h4 className="text-sm font-semibold text-muted-foreground">
                {t('redline.proposed')}
              </h4>
              <p
                lang={scriptOf(proposed ?? '') ?? redline.language}
                className="mt-1 whitespace-pre-line leading-relaxed"
              >
                {proposed}
              </p>
            </div>
          </div>
          {redline.rationale !== '' && (
            <p lang={redline.language} className="mt-3 text-muted-foreground">
              <strong className="text-foreground">{t('redline.rationale')}: </strong>
              {redline.rationale}
            </p>
          )}
        </div>
      ) : (
        <>
          <div lang={redline.language} className="mt-3">
            <h4 className="text-sm font-semibold text-muted-foreground">{t('redline.changes')}</h4>
            <p className="mt-1 whitespace-pre-line rounded-lg border border-border bg-background/50 p-4 leading-relaxed">
              {redline.diff.map((part, index) => {
                if (part.op === 'DELETE') {
                  return (
                    <del key={index} className="bg-rejected/15 text-rejected">
                      <span className="sr-only">{t('redline.removed')}: </span>
                      {part.text}
                    </del>
                  )
                }
                if (part.op === 'INSERT') {
                  return (
                    <ins key={index} className="bg-verified/15 text-verified underline">
                      <span className="sr-only">{t('redline.added')}: </span>
                      {part.text}
                    </ins>
                  )
                }
                return <span key={index}>{part.text}</span>
              })}
            </p>
            {redline.rationale !== '' && (
              <p className="mt-3 text-muted-foreground">
                <strong className="text-foreground">{t('redline.rationale')}: </strong>
                {redline.rationale}
              </p>
            )}
          </div>

          <details className="mt-3">
            <summary className="flex min-h-11 cursor-pointer items-center font-semibold">
              {t('redline.proposed')}
            </summary>
            <p lang={redline.language} className="mt-1 whitespace-pre-line leading-relaxed">
              {proposed ?? t('redline.none')}
            </p>
          </details>
        </>
      )}

      {redline.citations.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {groupCitations(redline.citations).map((group) => (
            <CitationChip
              key={group.sectionKey}
              group={group}
              language={uiLanguage}
              onOpen={onOpenCitation}
            />
          ))}
        </div>
      )}

      {proposed !== null && (
        <div className="mt-3">
          <Button
            variant="outline"
            onClick={() => {
              void navigator.clipboard.writeText(proposed).then(() => {
                setCopied(true)
              })
            }}
          >
            <Icon name={copied ? 'check' : 'copy'} />
            {copied ? t('redline.copied') : t('redline.copy')}
          </Button>
        </div>
      )}
    </GlassCard>
  )
}
