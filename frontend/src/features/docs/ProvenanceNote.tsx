import { useTranslation } from 'react-i18next'
import type { Language } from '../../i18n'
import { formatDateTime } from '../../lib/format'
import type { Provenance } from './reports'

/** Where a generated report came from: its own file, date, commit and corpus release (R-07). */
export function ProvenanceNote({
  file,
  provenance,
  language,
}: {
  file: string
  provenance: Provenance
  language: Language
}) {
  const { t } = useTranslation()
  return (
    <details className="glass-panel mt-4 rounded-xl px-4 py-2">
      <summary className="min-h-11 cursor-pointer py-2 font-semibold">
        {t('docs.provenance.title')}
      </summary>
      <dl className="grid gap-x-6 gap-y-1 pb-3 sm:grid-cols-[max-content_1fr]">
        <dt className="text-muted-foreground">{t('docs.provenance.file')}</dt>
        <dd>
          <code lang="en" dir="ltr">
            {file}
          </code>
        </dd>
        <dt className="text-muted-foreground">{t('docs.provenance.generated')}</dt>
        <dd>{formatDateTime(provenance.generatedAt, language)}</dd>
        <dt className="text-muted-foreground">{t('docs.provenance.commit')}</dt>
        <dd>
          <code lang="en" dir="ltr">
            {provenance.gitSha}
          </code>
        </dd>
        <dt className="text-muted-foreground">{t('docs.provenance.release')}</dt>
        <dd>
          <code lang="en" dir="ltr">
            {provenance.corpusRelease}
          </code>
        </dd>
      </dl>
    </details>
  )
}
