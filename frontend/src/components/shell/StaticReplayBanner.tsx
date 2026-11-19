import { useTranslation } from 'react-i18next'
import { isStaticReplay } from '../../api/replay'
import { Icon } from '../ui/Icon'

/**
 * Shown on every screen of the static recorded replay (FR-85, ADR-0027, R-12): there is no server
 * behind this build, only the recorded answers for one sample contract.
 */
export function StaticReplayBanner() {
  const { t } = useTranslation()
  if (!isStaticReplay()) {
    return null
  }
  return (
    // Opaque underneath, so the landing scene never shows through the banner.
    <div role="note" aria-label={t('replay.label')} className="relative z-10 bg-background text-sm">
      <div className="border-b border-rejected/40 bg-rejected/10">
        <p className="mx-auto flex max-w-6xl items-start gap-2 px-(--gutter) py-2">
          <Icon name="info" className="mt-1 shrink-0 text-rejected" />
          <span>
            <strong>{t('replay.label')}</strong> {t('replay.explain')}
          </span>
        </p>
      </div>
    </div>
  )
}
