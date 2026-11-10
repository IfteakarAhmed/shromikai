import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

/** The Bangla 403 page shown when a signed-in user opens a page their role does not allow. */
export function ForbiddenPage() {
  const { t } = useTranslation()
  return (
    <section aria-labelledby="forbidden-heading">
      <h1 id="forbidden-heading" className="text-2xl font-bold">
        {t('forbidden.title')}
      </h1>
      <p role="alert" className="mt-3 text-lg">
        {t('forbidden.body')}
      </p>
      <p className="mt-6">
        <Link to="/" className="font-medium underline underline-offset-4">
          {t('common.home')}
        </Link>
      </p>
    </section>
  )
}
