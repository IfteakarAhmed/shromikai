import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { GlassCard } from '../../components/ui/GlassCard'
import { useTitle } from '../../lib/useTitle'

const CARDS = [
  { to: '/docs/architecture', key: 'architecture' },
  { to: '/docs/data', key: 'data' },
  { to: '/docs/security', key: 'security' },
  { to: '/docs/audit', key: 'audit' },
  { to: '/docs/evaluation', key: 'evaluation' },
  { to: '/docs/prompts', key: 'prompts' },
  { to: '/docs/mcp', key: 'mcp' },
  { to: '/docs/known-gaps', key: 'gaps' },
] as const

/** `/docs`: where to read what. */
export default function DocsIndex() {
  const { t } = useTranslation()
  useTitle(t('docs.title'))
  return (
    <div>
      <h1 className="text-3xl font-bold">{t('docs.title')}</h1>
      <p className="mt-2 max-w-2xl text-lg text-muted-foreground">{t('docs.intro')}</p>
      <ul className="mt-6 grid gap-4 sm:grid-cols-2">
        {CARDS.map((card) => (
          <li key={card.key}>
            <GlassCard interactive className="h-full p-5">
              <h2 className="text-lg font-bold">
                <Link to={card.to} className="underline-offset-4 hover:underline">
                  {t(`docs.nav.${card.key}`)}
                </Link>
              </h2>
              <p className="mt-2 text-muted-foreground">{t(`docs.cards.${card.key}`)}</p>
            </GlassCard>
          </li>
        ))}
      </ul>
    </div>
  )
}
