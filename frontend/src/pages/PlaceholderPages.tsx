import { useTranslation } from 'react-i18next'

// P2 landing pages. The worker journey arrives in P9 and the admin console in P9 (FR-73).
export function AppHomePage() {
  const { t } = useTranslation()
  return (
    <section>
      <h1 className="text-2xl font-bold">{t('nav.analyse')}</h1>
      <p className="mt-3">{t('pages.appSoon')}</p>
    </section>
  )
}

export function AdminHomePage() {
  const { t } = useTranslation()
  return (
    <section>
      <h1 className="text-2xl font-bold">{t('nav.admin')}</h1>
      <p className="mt-3">{t('pages.adminSoon')}</p>
    </section>
  )
}
