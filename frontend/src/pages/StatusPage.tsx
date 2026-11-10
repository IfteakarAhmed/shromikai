import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ApiError, getMeta } from '../api/client'
import { Link } from 'react-router-dom'
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
    <div lang={language}>
      <h1 className="text-3xl font-bold">{t('app.name')}</h1>
      <p className="mt-2 text-lg text-stone-700">{t('app.tagline')}</p>

      <section
        aria-labelledby="status-heading"
        className="mt-8 rounded-lg border border-stone-200 bg-white p-5"
      >
        <h2 id="status-heading" className="font-semibold">
          {t('status.heading')}
        </h2>

        {meta.isPending && (
          <p role="status" className="mt-2 text-stone-600">
            {t('status.loading')}
          </p>
        )}

        {meta.isError && (
          <div role="alert" className="mt-2">
            <p className="text-red-800">{errorMessage}</p>
            <button
              type="button"
              onClick={() => void meta.refetch()}
              className="mt-3 min-h-11 rounded-md bg-emerald-800 px-4 py-2 text-white hover:bg-emerald-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
            >
              {t('status.retry')}
            </button>
          </div>
        )}

        {meta.isSuccess && (
          <dl className="mt-3 grid grid-cols-2 gap-y-2 text-base">
            <dt className="text-stone-600">{t('status.backend')}</dt>
            <dd data-testid="backend-status">
              {t('status.running', { version: formatVersion(meta.data.version, language) })}
            </dd>
            <dt className="text-stone-600">{t('status.acts')}</dt>
            <dd>{t('status.count', { count: formatNumber(meta.data.corpus.acts, language) })}</dd>
            <dt className="text-stone-600">{t('status.sections')}</dt>
            <dd>
              {t('status.count', {
                count: formatNumber(meta.data.corpus.currentSections, language),
              })}
            </dd>
            <dt className="text-stone-600">{t('status.embedded')}</dt>
            <dd>
              {t('status.count', {
                count: formatNumber(meta.data.corpus.embeddedSections, language),
              })}
            </dd>
          </dl>
        )}
      </section>

      <p className="mt-6">
        <Link
          to="/login"
          className="inline-flex min-h-11 items-center rounded-md bg-emerald-800 px-4 py-2 font-medium text-white hover:bg-emerald-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
        >
          {t('nav.signIn')}
        </Link>
      </p>
    </div>
  )
}
