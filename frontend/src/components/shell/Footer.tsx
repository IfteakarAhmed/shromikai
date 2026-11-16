import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Disclaimer } from '../ui/Disclaimer'

export function Footer() {
  const { t } = useTranslation()
  return (
    <footer className="relative z-10 mt-12 border-t border-border bg-background">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-(--gutter) py-8">
        <Disclaimer />
        <nav
          aria-label={t('footer.label')}
          className="flex flex-wrap gap-x-6 gap-y-1 text-muted-foreground"
        >
          <Link to="/docs" className="inline-flex min-h-11 items-center hover:text-foreground">
            {t('footer.docs')}
          </Link>
          <Link
            to="/docs/security"
            className="inline-flex min-h-11 items-center hover:text-foreground"
          >
            {t('footer.security')}
          </Link>
          <Link
            to="/docs/known-gaps"
            className="inline-flex min-h-11 items-center hover:text-foreground"
          >
            {t('footer.knownGaps')}
          </Link>
        </nav>
        <p className="text-sm text-muted-foreground">{t('footer.copyright')}</p>
      </div>
    </footer>
  )
}
