import { useTranslation } from 'react-i18next'
import { useTitle } from '../../lib/useTitle'

/** The admin console arrives in the next increment (FR-73). */
export default function AdminHome() {
  const { t } = useTranslation()
  useTitle(t('nav.admin'))
  return (
    <section className="mx-auto max-w-6xl px-(--gutter) py-10">
      <h1 className="text-3xl font-bold">{t('nav.admin')}</h1>
      <p className="mt-3 text-muted-foreground">{t('pages.adminSoon')}</p>
    </section>
  )
}
