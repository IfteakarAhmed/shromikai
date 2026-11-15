import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { VerifiedCitation } from '../../api/analyses'
import { getSection } from '../../api/laws'
import { useAuthedCall } from '../../api/useAuthedCall'
import { Sheet } from '../../components/ui/Sheet'
import { Skeleton } from '../../components/ui/Skeleton'
import { Icon } from '../../components/ui/Icon'
import { localizeDigits } from '../../lib/format'
import { locateQuote, splitAround } from '../../lib/highlight'
import { safeHref } from '../../lib/url'
import type { Language } from '../../i18n'

interface CitationSheetProps {
  citation: VerifiedCitation | null
  onClose: () => void
}

/**
 * The exact text of the cited section with the quoted words highlighted, the act, section and
 * version, and a link to the official source (FR-70). The section comes from the statute corpus;
 * if it cannot be fetched the verified quote itself is shown, never anything else.
 */
export function CitationSheet({ citation, onClose }: CitationSheetProps) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const call = useAuthedCall()
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
                <blockquote
                  lang={citation.quoteLanguage}
                  className="rounded-lg border border-border bg-background/50 p-4 leading-relaxed"
                >
                  <mark className="quote">{citation.quote}</mark>
                </blockquote>
              </>
            )}
            {section.isSuccess && (
              <SectionText
                text={section.data.authoritativeText}
                quote={citation.quote}
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
  quote,
  language,
}: {
  text: string
  quote: string
  language: Language
}) {
  const { t } = useTranslation()
  const found = locateQuote(text, quote)
  if (found === null) {
    return (
      <>
        <p lang={language} className="whitespace-pre-line leading-relaxed">
          {text}
        </p>
        <p className="mt-3 text-sm text-muted-foreground">{t('citationSheet.quoteNotInText')}</p>
        <blockquote
          lang={language}
          className="mt-2 rounded-lg border border-border bg-background/50 p-4 leading-relaxed"
        >
          {quote}
        </blockquote>
      </>
    )
  }
  const parts = splitAround(found.text, found.span)
  return (
    <>
      <p lang={language} className="whitespace-pre-line leading-relaxed">
        {parts.before}
        <mark className="quote">{parts.quote}</mark>
        {parts.after}
      </p>
      <p className="mt-3 text-sm text-muted-foreground">{t('citationSheet.highlightNote')}</p>
    </>
  )
}
