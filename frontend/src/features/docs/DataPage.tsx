import { TableShell } from '../../components/ui/TableShell'
import { useTranslation } from 'react-i18next'
import { GlassCard } from '../../components/ui/GlassCard'
import type { Language } from '../../i18n'
import { formatNumber } from '../../lib/format'
import { useTitle } from '../../lib/useTitle'
import { ProvenanceNote } from './ProvenanceNote'
import { corpusQuality } from './reports'

/** `/docs/data` (FR-74): the statute corpus and how complete it is, from `eval/reports/corpus-quality.json`. */
export default function DataPage() {
  const { t, i18n } = useTranslation()
  useTitle(t('docs.nav.data'))
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const quality = corpusQuality()
  const n = (value: number) => formatNumber(value, language)

  return (
    <div>
      <h1 className="text-3xl font-bold">{t('docs.data.title')}</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">{t('docs.data.intro')}</p>
      {quality === null ? (
        <p className="mt-6">{t('docs.data.noReport')}</p>
      ) : (
        <>
          <p className="mt-4 text-lg font-semibold">
            {t('docs.data.status')}:{' '}
            {t(`docs.data.statuses.${quality.status}`, { defaultValue: quality.status })}
          </p>
          <ProvenanceNote
            file="eval/reports/corpus-quality.json"
            provenance={quality.provenance}
            language={language}
          />

          <h2 className="mt-8 text-2xl font-bold">{t('docs.data.actsTitle')}</h2>
          <TableShell label={t('docs.data.caption')} minWidth="max-content" className="mt-3">
            <caption className="sr-only">{t('docs.data.caption')}</caption>
            <thead>
              <tr className="border-b border-border">
                <th scope="col" className="px-3 py-2 text-start">
                  {t('docs.data.colAct')}
                </th>
                <th scope="col" className="px-3 py-2 text-start">
                  {t('docs.data.colLang')}
                </th>
                <th scope="col" className="px-3 py-2 text-end">
                  {t('docs.data.colSections')}
                </th>
                <th scope="col" className="px-3 py-2 text-end">
                  {t('docs.data.colVersions')}
                </th>
                <th scope="col" className="px-3 py-2 text-end">
                  {t('docs.data.colNoTranslation')}
                </th>
                <th scope="col" className="px-3 py-2 text-start">
                  {t('docs.data.colSource')}
                </th>
              </tr>
            </thead>
            <tbody>
              {quality.acts.map((act) => (
                <tr key={act.code} className="border-b border-border align-top last:border-b-0">
                  <th scope="row" className="px-3 py-2 text-start font-medium">
                    <span lang={act.authoritativeLang === 'bn' ? 'bn' : 'en'}>{act.title}</span>
                    <br />
                    <code lang="en" dir="ltr" className="text-sm text-muted-foreground">
                      {act.code}
                    </code>
                  </th>
                  <td className="px-3 py-2">
                    {t(`docs.data.langs.${act.authoritativeLang}`, {
                      defaultValue: act.authoritativeLang,
                    })}
                  </td>
                  <td className="px-3 py-2 text-end">{n(act.currentSections)}</td>
                  <td className="px-3 py-2 text-end">{n(act.versions)}</td>
                  <td className="px-3 py-2 text-end">{n(act.missingTranslation)}</td>
                  <td className="px-3 py-2">
                    <a
                      href={act.sourceUrl}
                      rel="noopener noreferrer"
                      className="inline-flex min-h-11 items-center underline underline-offset-4"
                    >
                      {t('docs.data.source')}
                      <span className="sr-only"> · {act.code}</span>
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </TableShell>

          <h2 className="mt-8 text-2xl font-bold">{t('docs.data.edgesTitle')}</h2>
          <ul className="mt-3 grid gap-3 sm:grid-cols-2">
            {Object.entries(quality.edges).map(([kind, count]) => (
              <li key={kind}>
                <GlassCard className="flex items-center justify-between gap-3 px-4 py-3">
                  <span>{t(`docs.data.edges.${kind}`, { defaultValue: kind })}</span>
                  <span className="text-xl font-bold">{n(count)}</span>
                </GlassCard>
              </li>
            ))}
            <li>
              <GlassCard className="flex items-center justify-between gap-3 px-4 py-3">
                <span>{t('docs.data.unresolved')}</span>
                <span className="text-xl font-bold">{n(quality.unresolvedReferences)}</span>
              </GlassCard>
            </li>
          </ul>

          <h2 className="mt-8 text-2xl font-bold">{t('docs.data.iloTitle')}</h2>
          <p className="mt-1 text-muted-foreground">{t('docs.data.iloNote')}</p>
          <ul className="mt-3 grid gap-2 sm:grid-cols-2">
            {quality.ilo.map((row) => (
              <GlassCard as="li" key={row.code} className="px-4 py-3">
                <code lang="en" dir="ltr" className="font-semibold">
                  {row.code}
                </code>{' '}
                <span lang="en">{row.label}</span>
                <br />
                <span className="text-sm text-muted-foreground">
                  {t(`docs.data.iloStatuses.${row.status}`, { defaultValue: row.status })}
                </span>
              </GlassCard>
            ))}
          </ul>

          <h2 className="mt-8 text-2xl font-bold">{t('docs.data.missingTitle')}</h2>
          {quality.missing.length === 0 ? (
            <p className="mt-2 text-muted-foreground">{t('docs.data.missingNone')}</p>
          ) : (
            <ul className="mt-3 space-y-2">
              {quality.missing.map((row) => (
                <GlassCard as="li" key={row.what} className="px-4 py-3">
                  <code lang="en" dir="ltr" className="font-semibold">
                    {row.what}
                  </code>
                  <p lang="en" className="mt-1 text-sm text-muted-foreground">
                    {row.reason}
                  </p>
                </GlassCard>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}
