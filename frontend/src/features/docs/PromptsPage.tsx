import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { GlassCard } from '../../components/ui/GlassCard'
import { useTitle } from '../../lib/useTitle'
import { DocBody, DocPage } from './DocPage'
import { PROMPT_IDS } from './sources'

/** `/docs/prompts` and `/docs/prompts/:id` (FR-74): the prompt library, one page per prompt. */
export default function PromptsPage() {
  const { id } = useParams()
  const { t } = useTranslation()
  useTitle(t('docs.nav.prompts'))

  if (id !== undefined) {
    return PROMPT_IDS.includes(id) ? (
      <div>
        <Link
          to="/docs/prompts"
          className="inline-flex min-h-11 items-center font-semibold underline underline-offset-4"
        >
          {t('docs.backToPrompts')}
        </Link>
        <DocPage title={id} path={`docs/prompts/${id}.md`} />
      </div>
    ) : (
      <p className="text-muted-foreground">{t('docs.missing')}</p>
    )
  }
  return (
    <div>
      <h1 className="text-3xl font-bold">{t('docs.promptLibrary')}</h1>
      <p className="mt-2 text-muted-foreground">{t('docs.promptsIntro')}</p>
      <ul className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {PROMPT_IDS.map((promptId) => (
          <li key={promptId}>
            <GlassCard interactive className="px-4 py-3">
              <Link
                to={`/docs/prompts/${promptId}`}
                className="inline-flex min-h-11 items-center break-all font-semibold underline-offset-4 hover:underline"
              >
                <code lang="en" dir="ltr">
                  {promptId}
                </code>
              </Link>
            </GlassCard>
          </li>
        ))}
      </ul>
      <p className="mt-6 text-muted-foreground">{t('docs.englishOnly')}</p>
      <div className="mt-4">
        <DocBody path="docs/prompts/README.md" baseLevel={2} />
      </div>
    </div>
  )
}
