import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { CitationGroup } from '../../lib/citations'
import { getSection } from '../../api/laws'
import { useAuthedCall } from '../../api/useAuthedCall'
import { Sheet } from '../../components/ui/Sheet'
import { Skeleton } from '../../components/ui/Skeleton'
import { Icon } from '../../components/ui/Icon'
import { localizeDigits } from '../../lib/format'
import { locateQuotes } from '../../lib/highlight'
import { safeHref } from '../../lib/url'
import type { Language } from '../../i18n'

interface CitationSheetProps {
  group: CitationGroup | null
  onClose: () => void
}

/**
 * The exact text of the cited section with the quoted words highlighted, the act, section and
 * version, and a link to the official source (FR-70). The section comes from the statute corpus;
 * if it cannot be fetched the verified quote itself is shown, never anything else.
 */
export function CitationSheet({ group, onClose }: CitationSheetProps) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const call = useAuthedCall()
  const citation = group?.primary ?? null
  const section = useQuery({
    queryKey: ['section', citation?.sectionKey],
    enabled: citation !== null,
    queryFn: () =>
      citation === null
        ? Promise.reject(new Error('no citation'))
        : call((token, lang) =>
            getSection(citation.actCode, citation.sectionNo, citation.versionLabel, lang, token),
          ),
    retry: false,
  })

  const act =
    citation === null
      ? ''
      : ((language === 'bn' ? citation.actTitleBn : citation.actTitleEn) ??
        citation.actTitleEn ??
        citation.actTitleBn ??
        citation.actCode)
  const heading =
    citation === null
      ? null
      : ((language === 'bn' ? citation.headingBn : citation.headingEn) ??
        citation.headingEn ??
        citation.headingBn)
  const href = safeHref(section.data?.sourceUrl ?? citation?.sourceUrl)
  const number = citation === null ? '' : localizeDigits(citation.sectionNo, language)

  return (
    <Sheet
      open={citation !== null}
      onClose={onClose}
      title={
        citation === null
          ? t('citationSheet.title')
          : `${act} · ${t('citation.section', { number })}`
      }
      description={heading ?? undefined}
    >
      {citation !== null && (
        <div className="space-y-5">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            <dt className="text-muted-foreground">{t('citationSheet.act')}</dt>
            <dd lang={language}>{act}</dd>
            <dt className="text-muted-foreground">{t('citationSheet.section')}</dt>
            <dd>{number}</dd>
            <dt className="text-muted-foreground">{t('citationSheet.version')}</dt>
            <dd>{citation.versionLabel}</dd>
            {citation.effectiveFrom !== null && (
              <>
                <dt className="text-muted-foreground">{t('citationSheet.effective')}</dt>
                <dd>{localizeDigits(citation.effectiveFrom, language)}</dd>
              </>
            )}
          </dl>

          <section aria-labelledby="sheet-text">
            <h3 id="sheet-text" className="mb-2 font-semibold">
              {section.isSuccess ? t('citationSheet.fullText') : t('citationSheet.quoted')}
            </h3>
            {section.isPending && (
              <div role="status" aria-label={t('citationSheet.loading')} className="space-y-2">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-11/12" />
                <Skeleton className="h-4 w-3/4" />
              </div>
            )}
            {section.isError && (
              <>
                <p className="mb-2 text-sm text-muted-foreground">
                  {t('citationSheet.unavailable')}
                </p>
                {(group?.quotes ?? [citation.quote]).map((quote) => (
                  <blockquote
                    key={quote}
                    lang={citation.quoteLanguage}
                    className="mb-2 rounded-lg border border-border bg-background/50 p-4 leading-relaxed"
                  >
                    <mark className="quote">{quote}</mark>
                  </blockquote>
                ))}
              </>
            )}
            {section.isSuccess && (
              <SectionText
                text={section.data.authoritativeText}
                quotes={group?.quotes ?? [citation.quote]}
                language={section.data.authoritativeLang === 'en' ? 'en' : 'bn'}
              />
            )}
          </section>

          {section.isSuccess && section.data.translation !== null && (
            <section aria-labelledby="sheet-translation">
              <h3 id="sheet-translation" className="mb-1 font-semibold">
                {t('citationSheet.translation')}
              </h3>
              <p className="mb-2 text-sm text-muted-foreground">
                {section.data.translation.official
                  ? t('citationSheet.translationOfficial')
                  : t('citationSheet.translationUnofficial')}
              </p>
              <p lang={section.data.translation.lang} className="leading-relaxed">
                {section.data.translation.text}
              </p>
            </section>
          )}

          {href !== null && (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-verified/50 bg-verified/10 px-4 font-semibold text-verified hover:bg-verified/20"
            >
              <Icon name="external" />
              {t('citationSheet.official')}
              <span className="sr-only"> ({t('citation.opensInNewTab')})</span>
            </a>
          )}
        </div>
      )}
    </Sheet>
  )
}

function SectionText({
  text,
  quotes,
  language,
}: {
  text: string
  quotes: readonly string[]
  language: Language
}) {
  const { t } = useTranslation()
  const found = locateQuotes(text, quotes)
  const pieces: { text: string; marked: boolean }[] = []
  let cursor = 0
  for (const span of found.spans) {
    pieces.push({ text: found.text.slice(cursor, span.start), marked: false })
    pieces.push({ text: found.text.slice(span.start, span.end), marked: true })
    cursor = span.end
  }
  pieces.push({ text: found.text.slice(cursor), marked: false })
  return (
    <>
      <p lang={language} className="whitespace-pre-line leading-relaxed">
        {pieces.map((piece, index) =>
          piece.marked ? (
            <mark key={index} className="quote">
              {piece.text}
            </mark>
          ) : (
            piece.text
          ),
        )}
      </p>
      {found.spans.length > 0 && (
        <p className="mt-3 text-sm text-muted-foreground">{t('citationSheet.highlightNote')}</p>
      )}
      {found.missing > 0 && (
        <>
          <p className="mt-3 text-sm text-muted-foreground">{t('citationSheet.quoteNotInText')}</p>
          {quotes.map((quote) => (
            <blockquote
              key={quote}
              lang={language}
              className="mt-2 rounded-lg border border-border bg-background/50 p-4 leading-relaxed"
            >
              {quote}
            </blockquote>
          ))}
        </>
      )}
    </>
  )
}
