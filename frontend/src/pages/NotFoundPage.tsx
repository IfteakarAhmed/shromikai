import { useTranslation } from 'react-i18next'
import { LinkButton } from '../components/ui/Button'
import { useTitle } from '../lib/useTitle'

export function NotFoundPage() {
  const { t } = useTranslation()
  useTitle(t('notFound.title'))
  return (
    <section className="mx-auto max-w-2xl px-(--gutter) py-12">
      <h1 className="text-3xl font-bold">{t('notFound.title')}</h1>
      <div className="mt-6">
        <LinkButton to="/" variant="outline">
          {t('common.home')}
        </LinkButton>
      </div>
    </section>
  )
}
