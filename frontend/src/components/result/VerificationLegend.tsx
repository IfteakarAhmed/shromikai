import { useTranslation } from 'react-i18next'
import { VerificationBadge } from './VerificationBadge'

/** What the two marks mean (FR-70). */
export function VerificationLegend() {
  const { t } = useTranslation()
  return (
    <section aria-label={t('verification.legendTitle')}>
      <h3 className="mb-2 text-sm font-semibold text-muted-foreground">
        {t('verification.legendTitle')}
      </h3>
      <ul className="grid gap-2 sm:grid-cols-2">
        <li className="flex items-start gap-2">
          <VerificationBadge status="VERIFIED" />
          <span className="text-sm text-muted-foreground">{t('verification.verifiedMeaning')}</span>
        </li>
        <li className="flex items-start gap-2">
          <VerificationBadge status="UNCERTAIN" />
          <span className="text-sm text-muted-foreground">
            {t('verification.uncertainMeaning')}
          </span>
        </li>
      </ul>
    </section>
  )
}
