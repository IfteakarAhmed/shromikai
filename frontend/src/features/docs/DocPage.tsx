import { useTranslation } from 'react-i18next'
import { ErrorState } from '../../components/ui/ErrorState'
import { Skeleton } from '../../components/ui/Skeleton'
import { Markdown } from './markdown'
import { useDocument } from './sources'

/**
 * A repository document shown as a page. The documents are written in English, so the page says so
 * and marks the text as English for screen readers and hyphenation.
 */
export function DocBody({
  path,
  source,
  baseLevel = 2,
}: {
  path?: string
  source?: string
  baseLevel?: number
}) {
  const { t } = useTranslation()
  const doc = useDocument(path ?? '')
  const text = source ?? doc.data
  if (source === undefined && path !== undefined && doc.isPending) {
    return (
      <div role="status" aria-label={t('docs.loading')} className="space-y-3">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }
  if (source === undefined && doc.isError) {
    return (
      <ErrorState
        error={doc.error}
        onRetry={() => {
          void doc.refetch()
        }}
      />
    )
  }
  if (text === null || text === undefined) {
    return <p className="text-muted-foreground">{t('docs.missing')}</p>
  }
  return (
    <div lang="en" dir="ltr">
      <Markdown
        source={text}
        baseLevel={baseLevel}
        mermaidNote={t('docs.mermaid')}
        tableLabel={t('docs.table')}
      />
    </div>
  )
}

/** A whole document as a page: the note about its language, then the text. */
export function DocPage({ title, path }: { title: string; path: string }) {
  const { t } = useTranslation()
  return (
    <div>
      <h1 className="text-3xl font-bold">{title}</h1>
      <p className="mt-2 text-muted-foreground">{t('docs.englishOnly')}</p>
      <div className="mt-6">
        <DocBody path={path} />
      </div>
    </div>
  )
}
