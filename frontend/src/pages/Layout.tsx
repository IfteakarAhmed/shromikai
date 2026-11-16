import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Outlet, useLocation } from 'react-router-dom'
import { Footer } from '../components/shell/Footer'
import { Header } from '../components/shell/Header'
import { RecordedBanner } from '../components/shell/RecordedBanner'

/** Skip link, sticky header, the recorded-demo banner, the page and the footer. */
export function Layout() {
  const { t } = useTranslation()
  const { pathname } = useLocation()

  // A new page starts at the top, like a page load.
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])

  // The landing keeps the ink palette in either theme: its scene is drawn on ink (D1 plan).
  const ink = pathname === '/'

  return (
    <div className="flex min-h-dvh flex-col" data-surface={ink ? 'ink' : undefined}>
      <a
        href="#main"
        className="sr-only z-50 rounded-lg bg-primary px-4 py-3 font-semibold text-primary-foreground focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        {t('nav.skip')}
      </a>
      <Header />
      <RecordedBanner />
      <main id="main" tabIndex={-1} className="flex-1 outline-none">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
