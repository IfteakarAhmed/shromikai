import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

/** Sets the document title to "page · brand" while the page is shown. */
export function useTitle(title: string): void {
  const { t } = useTranslation()
  const brand = t('app.name')
  useEffect(() => {
    const previous = document.title
    document.title = `${title} · ${brand}`
    return () => {
      document.title = previous
    }
  }, [title, brand])
}
