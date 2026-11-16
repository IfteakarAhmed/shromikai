import { useTranslation } from 'react-i18next'
import { useTitle } from '../../lib/useTitle'
import { DocPage } from './DocPage'

/** `/docs/known-gaps`: what does not work yet; the text is `docs/known-gaps.md`. */
export default function KnownGapsPage() {
  const { t } = useTranslation()
  useTitle(t('docs.nav.gaps'))
  return <DocPage title={t('docs.nav.gaps')} path="docs/known-gaps.md" />
}
