import { useTranslation } from 'react-i18next'
import type { ReviewItem } from '../../api/admin'

/** What an item is about, in words: its clause, or that the statute text changed. */
export function useItemTitle() {
  const { t } = useTranslation()
  return (item: ReviewItem) =>
    item.kind === 'CORPUS_CHANGE' || item.clauseType === null
      ? t('admin.review.corpusChange')
      : t(`clauseType.${item.clauseType}`)
}
