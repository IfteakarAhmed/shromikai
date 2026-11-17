import { useTranslation } from 'react-i18next'
import { LinkButton } from '../components/ui/Button'
import { StatusPage } from '../components/ui/StatusPage'
import { useTitle } from '../lib/useTitle'

/** The Bangla 403 page shown when a signed-in user opens a page their role does not allow. */
export function ForbiddenPage() {
  const { t } = useTranslation()
  useTitle(t('forbidden.title'))
  return (
    <StatusPage
      code="403"
      headingId="forbidden-heading"
      title={t('forbidden.title')}
      actions={
        <>
          <LinkButton to="/login">{t('forbidden.switch')}</LinkButton>
          <LinkButton to="/" variant="outline">
            {t('common.home')}
          </LinkButton>
        </>
      }
    >
      <p role="alert">{t('forbidden.body')}</p>
    </StatusPage>
  )
}
