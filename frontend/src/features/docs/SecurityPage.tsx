import { useTranslation } from 'react-i18next'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon, type IconName } from '../../components/ui/Icon'
import { useTitle } from '../../lib/useTitle'
import { DocBody } from './DocPage'
import { sectionOf } from './markdownText'
import { useDocument } from './sources'

const SPEC = 'docs/spec/architecture.md'
const CARDS: { key: 'noStorage' | 'redacted' | 'verified'; icon: IconName }[] = [
  { key: 'noStorage', icon: 'lock' },
  { key: 'redacted', icon: 'shield' },
  { key: 'verified', icon: 'check' },
]

/** `/docs/security` (FR-74): the privacy promises, then the privacy pipeline, threat model and data lifecycle. */
export default function SecurityPage() {
  const { t } = useTranslation()
  useTitle(t('docs.nav.security'))
  const spec = useDocument(SPEC)
  const text =
    spec.data === undefined || spec.data === null
      ? undefined
      : [
          sectionOf(spec.data, '## 10. Privacy pipeline'),
          sectionOf(spec.data, '## 13. Threat model (STRIDE)'),
          sectionOf(spec.data, '## 14. Data lifecycle'),
        ].join('\n\n')
  return (
    <div>
      <h1 className="text-3xl font-bold">{t('docs.security.title')}</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">{t('docs.security.intro')}</p>
      <ul className="mt-5 grid gap-4 md:grid-cols-3">
        {CARDS.map((card) => (
          <li key={card.key}>
            <GlassCard className="h-full p-5">
              <Icon name={card.icon} size={22} className="text-primary" />
              <h2 className="mt-2 text-lg font-bold">{t(`docs.security.cards.${card.key}`)}</h2>
              <p className="mt-1 text-muted-foreground">
                {t(`docs.security.cards.${card.key}Body`)}
              </p>
            </GlassCard>
          </li>
        ))}
      </ul>
      <h2 className="mt-8 text-2xl font-bold">{t('docs.security.sections')}</h2>
      <p className="mt-1 text-muted-foreground">{t('docs.englishOnly')}</p>
      <div className="mt-4">
        {text === undefined ? <DocBody path={SPEC} /> : <DocBody source={text} baseLevel={3} />}
      </div>
    </div>
  )
}
