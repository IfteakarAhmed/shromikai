import { useTranslation } from 'react-i18next'
import type { CitationGroup } from '../../lib/citations'
import { localizeDigits } from '../../lib/format'
import type { Language } from '../../i18n'
import { Icon } from '../ui/Icon'

interface CitationChipProps {
  group: CitationGroup
  /** The language of the interface (not of the analysis): it picks the act title and the digits. */
  language: Language
  onOpen: (group: CitationGroup) => void
}

/**
 * A verified citation as a chip: the act and the section. It exists only for a `VerifiedCitation`,
 * so it cannot show anything the verifier did not pass (R-01). It opens the exact section text.
 */
export function CitationChip({ group, language, onOpen }: CitationChipProps) {
  const { t } = useTranslation()
  const citation = group.primary
  const act =
    (language === 'bn' ? citation.actTitleBn : citation.actTitleEn) ??
    citation.actTitleEn ??
    citation.actTitleBn ??
    citation.actCode
  const section = t('citation.section', { number: localizeDigits(citation.sectionNo, language) })
  return (
    <button
      type="button"
      onClick={() => {
        onOpen(group)
      }}
      className="inline-flex min-h-11 max-w-full items-center gap-2 rounded-lg border border-border bg-background/60 px-3 py-2 text-left transition-colors hover:border-verified/60 hover:bg-verified/5"
    >
      <Icon name="scale" className="shrink-0 text-verified" />
      <span className="min-w-0 truncate">{act}</span>{' '}
      <span className="shrink-0 rounded bg-secondary px-1.5 py-0.5 text-sm font-semibold">
        {section}
      </span>{' '}
      {group.showVersion && (
        <span className="shrink-0 text-sm text-muted-foreground">
          {t('citation.version', { label: citation.versionLabel })}
        </span>
      )}{' '}
      <span className="sr-only">{t('citation.openHint')}</span>
    </button>
  )
}
