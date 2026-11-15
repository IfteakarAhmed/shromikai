import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { renderMemo, type AnalysisResult } from '../../api/analyses'
import { useAuthedCall } from '../../api/useAuthedCall'
import { Button } from '../../components/ui/Button'
import { Disclaimer } from '../../components/ui/Disclaimer'
import { Icon } from '../../components/ui/Icon'
import { memoSpeech, memoText } from '../../lib/memoText'
import { formatNumber, localizeDigits } from '../../lib/format'
import { speak, useHasVoice } from '../../lib/speech'
import { withoutRepeatedSections } from '../../lib/memoBlocks'
import type { Language } from '../../i18n'

type Block = AnalysisResult['memo']['sections'][number]['blocks'][number]

/**
 * The memo: a preview built from the memo sections of the result, a PDF from the server (E-21), a
 * print layout, copy and share, and read-aloud where the device has a voice for the language. The
 * text is the result's own; nothing is stored (R-02).
 */
export function MemoView({ result }: { result: AnalysisResult }) {
  const { t, i18n } = useTranslation()
  const uiLanguage: Language = i18n.language === 'en' ? 'en' : 'bn'
  const call = useAuthedCall()
  const { memo } = result
  const [copied, setCopied] = useState(false)
  const [speaking, setSpeaking] = useState(false)
  const stop = useRef<(() => void) | null>(null)
  const canListen = useHasVoice(memo.language)
  const canShare = typeof navigator.share === 'function'

  useEffect(
    () => () => {
      stop.current?.()
    },
    [],
  )

  const download = useMutation({
    mutationFn: () => call((token, lang) => renderMemo(result, 'PDF', lang, token)),
    onSuccess: ({ blob }) => {
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `memo-${result.analysisId}.pdf`
      link.click()
      URL.revokeObjectURL(url)
    },
  })

  const findings = new Map(result.findings.map((finding) => [finding.id, finding]))
  const redlines = new Map(result.redlines.map((redline) => [redline.id, redline]))
  const obligations = new Map(result.obligations.map((item) => [item.id, item]))
  const citations = new Map(
    result.findings
      .flatMap((finding) => finding.citations)
      .concat(result.redlines.flatMap((redline) => redline.citations))
      .map((citation) => [citation.citationId, citation]),
  )

  const sectionKeys = new Map(
    Array.from(citations.values()).map((citation) => [citation.citationId, citation.sectionKey]),
  )

  function renderBlock(block: Block, index: number) {
    switch (block.type) {
      case 'paragraph':
        return <p key={index}>{block.text}</p>
      case 'list':
        return (
          <ul key={index} className="list-disc ps-6">
            {block.items.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        )
      case 'riskTable':
        return (
          <table key={index} className="w-full border-collapse text-start">
            <caption className="sr-only">{t('memo.riskTable')}</caption>
            <thead>
              <tr>
                <th scope="col" className="border-b border-border py-1 text-start">
                  {t('memo.colRisk')}
                </th>
                <th scope="col" className="border-b border-border py-1 text-start">
                  {t('memo.colLevel')}
                </th>
                <th scope="col" className="border-b border-border py-1 text-start">
                  {t('memo.colStatus')}
                </th>
              </tr>
            </thead>
            <tbody>
              {block.findingIds.map((id) => {
                const finding = findings.get(id)
                return finding === undefined ? null : (
                  <tr key={id}>
                    <td className="border-b border-border py-1 pe-2">{finding.title}</td>
                    <td className="border-b border-border py-1 pe-2">
                      {t(`severity.${finding.severity.toLowerCase()}`)}
                    </td>
                    <td className="border-b border-border py-1">
                      {t(
                        finding.verificationStatus === 'VERIFIED'
                          ? 'verification.verified'
                          : finding.verificationStatus === 'UNCERTAIN'
                            ? 'verification.uncertain'
                            : 'verification.policy',
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )
      case 'findingRef': {
        const finding = findings.get(block.findingId)
        return finding === undefined ? null : (
          <h4 key={index} className="mt-3 font-bold">
            {finding.title}
          </h4>
        )
      }
      case 'citationChip': {
        const citation = citations.get(block.citationId)
        if (citation === undefined) {
          return null
        }
        const act =
          (uiLanguage === 'bn' ? citation.actTitleBn : citation.actTitleEn) ?? citation.actCode
        return (
          <p key={index} className="text-sm text-muted-foreground">
            ▸ {act},{' '}
            {t('citation.section', { number: localizeDigits(citation.sectionNo, uiLanguage) })}
          </p>
        )
      }
      case 'redlineRef': {
        const redline = redlines.get(block.redlineId)
        return redline?.proposedText == null ? null : (
          <div key={index} className="rounded-lg border border-border p-3">
            <p className="text-sm font-semibold text-muted-foreground">{t('memo.revised')}</p>
            <p className="whitespace-pre-line">{redline.proposedText}</p>
          </div>
        )
      }
      case 'obligationTable':
        return (
          <ul key={index} className="list-disc ps-6">
            {block.obligationIds.map((id) => {
              const item = obligations.get(id)
              return item === undefined ? null : <li key={id}>{item.obligation}</li>
            })}
          </ul>
        )
    }
  }

  return (
    <div>
      <div className="no-print flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold">{t('memo.title')}</h2>
          <p className="text-muted-foreground">{t('memo.intro')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            busy={download.isPending}
            onClick={() => {
              download.mutate()
            }}
          >
            <Icon name="download" />
            {download.isPending ? t('memo.downloading') : t('memo.download')}
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              window.print()
            }}
          >
            <Icon name="printer" />
            {t('memo.print')}
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              void navigator.clipboard.writeText(memoText(result)).then(() => {
                setCopied(true)
              })
            }}
          >
            <Icon name={copied ? 'check' : 'copy'} />
            {copied ? t('memo.copied') : t('memo.copy')}
          </Button>
          {canShare && (
            <Button
              variant="outline"
              onClick={() => {
                void navigator.share({ title: memo.title, text: memoSpeech(result) })
              }}
            >
              <Icon name="share" />
              {t('memo.share')}
            </Button>
          )}
          {canListen && (
            <Button
              variant="outline"
              aria-pressed={speaking}
              onClick={() => {
                if (speaking) {
                  stop.current?.()
                  setSpeaking(false)
                } else {
                  setSpeaking(true)
                  stop.current = speak(memoSpeech(result), memo.language, () => {
                    setSpeaking(false)
                  })
                }
              }}
            >
              <Icon name="play" />
              {speaking ? t('memo.stop') : t('memo.listen')}
            </Button>
          )}
        </div>
      </div>
      {download.isError && (
        <p
          role="alert"
          className="no-print mt-3 rounded-lg border border-rejected/40 bg-rejected/10 px-4 py-3 text-rejected"
        >
          {t('memo.downloadFailed')}
        </p>
      )}

      <article
        lang={memo.language}
        className="memo-print glass-panel mt-5 space-y-5 rounded-xl p-6"
        aria-label={memo.title}
      >
        <header>
          <h3 className="text-2xl font-bold">{memo.title}</h3>
          <p className="text-sm text-muted-foreground">
            {t('memo.generated', {
              time: new Date(memo.generatedAt).toLocaleString(
                uiLanguage === 'bn' ? 'bn-BD' : 'en-GB',
              ),
            })}
            {' · '}
            {t('memo.summary', {
              verified: formatNumber(memo.verificationSummary.verifiedCitations, uiLanguage),
              uncertain: formatNumber(memo.verificationSummary.uncertainFindings, uiLanguage),
            })}
          </p>
        </header>
        {memo.sections.map((section) => (
          <section key={section.kind} className="space-y-2">
            <h4 className="text-xl font-bold">{section.heading}</h4>
            {withoutRepeatedSections(section.blocks, sectionKeys).map(renderBlock)}
          </section>
        ))}
        <footer className="border-t border-border pt-3 text-sm text-muted-foreground">
          <p>{memo.disclaimer}</p>
          <p>
            {t('memo.footer', { id: memo.footer.analysisId, release: memo.footer.corpusRelease })}
          </p>
        </footer>
      </article>
      <div className="no-print mt-4">
        <Disclaimer text={result.disclaimer} />
      </div>
    </div>
  )
}
