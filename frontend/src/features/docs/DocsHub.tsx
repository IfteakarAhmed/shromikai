import { useTranslation } from 'react-i18next'
import { StatusPage } from '../../pages/StatusPage'
import { useTitle } from '../../lib/useTitle'

/** The `/docs` hub: for now the live status of the system; the other pages follow (FR-74). */
export default function DocsHub() {
  const { t } = useTranslation()
  useTitle(t('footer.docs'))
  return <StatusPage />
}
