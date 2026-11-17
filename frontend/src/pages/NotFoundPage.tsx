import { useTranslation } from 'react-i18next'
import { LinkButton } from '../components/ui/Button'
import { StatusPage } from '../components/ui/StatusPage'
import { useTitle } from '../lib/useTitle'

export function NotFoundPage() {
  const { t } = useTranslation()
  useTitle(t('notFound.title'))
  return (
    <StatusPage
      code="404"
      headingId="not-found-heading"
      title={t('notFound.title')}
      actions={
        <>
          <LinkButton to="/">{t('common.home')}</LinkButton>
          <LinkButton to="/app" variant="outline">
            {t('notFound.analyse')}
          </LinkButton>
          <LinkButton to="/docs" variant="outline">
            {t('nav.docs')}
          </LinkButton>
        </>
      }
    >
      <p>{t('notFound.body')}</p>
    </StatusPage>
  )
}
