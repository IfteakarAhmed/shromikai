import { useTranslation } from 'react-i18next'
import type { AnalysisResult, ObligationItem } from '../../api/analyses'
import { CitationChip } from '../../components/result/CitationChip'
import { Badge } from '../../components/ui/Badge'
import { GlassCard } from '../../components/ui/GlassCard'
import { groupCitations, type CitationGroup } from '../../lib/citations'
import { formatNumber, localizeDigits } from '../../lib/format'
import type { Language } from '../../i18n'

interface DutiesViewProps {
  result: AnalysisResult
  done: ReadonlySet<string>
  onToggle: (id: string) => void
  onOpenCitation: (group: CitationGroup) => void
}

type Party = 'WORKER' | 'EMPLOYER' | 'BOTH'

/**
 * The obligations checklist, one list per party with the reader's own side first. The ticks live in
 * the result page's memory only (R-02): they are not saved anywhere and are gone when the page closes.
 */
export function DutiesView({ result, done, onToggle, onOpenCitation }: DutiesViewProps) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const order: Party[] =
    result.side === 'WORKER' ? ['WORKER', 'EMPLOYER', 'BOTH'] : ['EMPLOYER', 'WORKER', 'BOTH']
  const total = result.obligations.length

  return (
    <div>
      <h2 className="text-2xl font-bold">{t('duties.title')}</h2>
      <p className="mt-1 text-muted-foreground">{t('duties.intro')}</p>
      <p className="mt-1 text-sm text-muted-foreground">{t('duties.checkNote')}</p>
      <p role="status" className="mt-2 font-semibold">
        {t('duties.progress', {
          done: formatNumber(done.size, language),
          total: formatNumber(total, language),
        })}
      </p>
      <div className="mt-4 grid gap-6 lg:grid-cols-2">
        {order.map((party) => {
          const items = result.obligations.filter((item) => item.party === party)
          return (
            <section key={party} aria-labelledby={`duties-${party}`}>
              <h3 id={`duties-${party}`} className="text-xl font-bold">
                {t(`duties.${party.toLowerCase()}`)}
              </h3>
              {items.length === 0 ? (
                <p className="mt-2 text-muted-foreground">{t('duties.none')}</p>
              ) : (
                <ul className="mt-3 grid gap-3">
                  {items.map((item) => (
                    <li key={item.id}>
                      <Duty
                        item={item}
                        checked={done.has(item.id)}
                        onToggle={() => {
                          onToggle(item.id)
                        }}
                        language={language}
                        contentLanguage={result.outputLanguage}
                        onOpenCitation={onOpenCitation}
                      />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )
        })}
      </div>
    </div>
  )
}

function Duty({
  item,
  checked,
  onToggle,
  language,
  contentLanguage,
  onOpenCitation,
}: {
  item: ObligationItem
  checked: boolean
  onToggle: () => void
  language: Language
  contentLanguage: Language
  onOpenCitation: (group: CitationGroup) => void
}) {
  const { t } = useTranslation()
  return (
    <GlassCard className="p-4">
      <label className="flex min-h-11 cursor-pointer items-start gap-3">
        <input
          type="checkbox"
          checked={checked}
          onChange={onToggle}
          className="mt-1.5 size-5 shrink-0 accent-[var(--primary)]"
        />
        <span
          lang={contentLanguage}
          className={checked ? 'text-muted-foreground line-through' : ''}
        >
          {item.obligation}
        </span>
      </label>
      <div className="lift-2 mt-2 flex flex-wrap items-center gap-2">
        <Badge tone={item.status === 'CONFLICTS_WITH_CONTRACT' ? 'rejected' : 'neutral'}>
          {t(`duties.status.${item.status}`)}
        </Badge>
        {item.deadline !== null && (
          <span className="text-sm text-muted-foreground">
            {t('duties.deadline', {
              value: localizeDigits(String(item.deadline.value), language),
              unit: t(`duties.unit.${item.deadline.unit}`),
            })}
          </span>
        )}
        {item.trigger !== null && (
          <span lang={contentLanguage} className="text-sm text-muted-foreground">
            {t('duties.trigger', { text: item.trigger })}
          </span>
        )}
      </div>
      {item.citations.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-2">
          {groupCitations(item.citations).map((group) => (
            <CitationChip
              key={group.sectionKey}
              group={group}
              language={language}
              onOpen={onOpenCitation}
            />
          ))}
        </div>
      )}
    </GlassCard>
  )
}
