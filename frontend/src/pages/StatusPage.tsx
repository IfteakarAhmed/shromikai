import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ApiError, getMeta } from '../api/client'
import { Button } from '../components/ui/Button'
import { type Language } from '../i18n'
import { formatNumber, formatVersion } from '../lib/format'

// Home page: shows that the frontend reaches the backend and the database. The worker journey
// (upload, risk cards, memo) arrives in P9.
export function StatusPage() {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const meta = useQuery({
    queryKey: ['meta', language],
    queryFn: () => getMeta(language),
    retry: false,
  })

  const errorMessage =
    meta.error instanceof ApiError && meta.error.problem
      ? t('status.error', { title: meta.error.problem.title })
      : t('status.down')

  return (
    <div lang={language} className="mx-auto max-w-3xl px-(--gutter) py-10">
      <h1 className="text-3xl font-bold">{t('app.name')}</h1>
      <p className="mt-2 text-lg text-muted-foreground">{t('app.tagline')}</p>

      <section aria-labelledby="status-heading" className="mt-8 glass-panel rounded-xl p-5">
        <h2 id="status-heading" className="font-semibold">
          {t('status.heading')}
        </h2>

        {meta.isPending && (
          <p role="status" className="mt-2 text-muted-foreground">
            {t('status.loading')}
          </p>
        )}

        {meta.isError && (
          <div role="alert" className="mt-2">
            <p className="text-rejected">{errorMessage}</p>
            <Button className="mt-3" onClick={() => void meta.refetch()}>
              {t('status.retry')}
            </Button>
          </div>
        )}

        {meta.isSuccess && (
          <dl className="mt-3 grid grid-cols-2 gap-y-2 text-base">
            <dt className="text-muted-foreground">{t('status.backend')}</dt>
            <dd data-testid="backend-status">
              {t('status.running', { version: formatVersion(meta.data.version, language) })}
            </dd>
            <dt className="text-muted-foreground">{t('status.acts')}</dt>
            <dd>{t('status.count', { count: formatNumber(meta.data.corpus.acts, language) })}</dd>
            <dt className="text-muted-foreground">{t('status.sections')}</dt>
            <dd>
              {t('status.count', {
                count: formatNumber(meta.data.corpus.currentSections, language),
              })}
            </dd>
            <dt className="text-muted-foreground">{t('status.embedded')}</dt>
            <dd>
              {t('status.count', {
                count: formatNumber(meta.data.corpus.embeddedSections, language),
              })}
            </dd>
          </dl>
        )}
      </section>
    </div>
  )
}
