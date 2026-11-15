import { useTranslation } from 'react-i18next'
import { LinkButton } from '../components/ui/Button'
import { useTitle } from '../lib/useTitle'

/** The Bangla 403 page shown when a signed-in user opens a page their role does not allow. */
export function ForbiddenPage() {
  const { t } = useTranslation()
  useTitle(t('forbidden.title'))
  return (
    <section aria-labelledby="forbidden-heading" className="mx-auto max-w-2xl px-(--gutter) py-12">
      <h1 id="forbidden-heading" className="text-3xl font-bold">
        {t('forbidden.title')}
      </h1>
      <p role="alert" className="mt-3 text-lg">
        {t('forbidden.body')}
      </p>
      <div className="mt-6">
        <LinkButton to="/" variant="outline">
          {t('common.home')}
        </LinkButton>
      </div>
    </section>
  )
}
