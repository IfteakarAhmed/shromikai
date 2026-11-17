import { useTranslation } from 'react-i18next'
import { formatNumber } from '../../lib/format'
import type { Language } from '../../i18n'

/**
 * The "verification finished" seal (C3, CSS and SVG only). The caller shows it only when
 * verification really finished (`verificationFinished`); the counts are the API's. It drops in
 * once with a soft glow; with reduced motion it simply stands there.
 */
export function SealMoment({ verified, uncertain }: { verified: number; uncertain: number }) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const n = (value: number) => formatNumber(value, language)
  const title = t('results.seal.title')
  return (
    <div role="status" aria-label={title} className="seal-moment flex items-center gap-4">
      <svg viewBox="0 0 96 96" width="88" height="88" aria-hidden="true" className="shrink-0">
        <circle cx="48" cy="48" r="44" className="seal-ring" />
        <circle cx="48" cy="48" r="36" className="seal-disc" />
        <circle cx="48" cy="48" r="30" className="seal-inner" />
        <path d="M33 49 L44 60 L64 37" className="seal-check" />
      </svg>
      <div>
        <p className="font-display text-2xl leading-tight text-verified">{title}</p>
        <p className="mt-1 text-muted-foreground">
          {uncertain > 0
            ? t('results.seal.detail', { verified: n(verified), uncertain: n(uncertain) })
            : t('results.seal.detailVerified', { verified: n(verified) })}
        </p>
      </div>
    </div>
  )
}
