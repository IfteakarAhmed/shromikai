import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

export function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <section>
      <h1 className="text-2xl font-bold">{t('notFound.title')}</h1>
      <p className="mt-6">
        <Link to="/" className="font-medium underline underline-offset-4">
          {t('common.home')}
        </Link>
      </p>
    </section>
  )
}
