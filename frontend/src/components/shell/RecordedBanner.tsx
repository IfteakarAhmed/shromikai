import { useTranslation } from 'react-i18next'
import { isStaticReplay } from '../../api/replay'
import { useMeta } from '../../api/useMeta'
import { Icon } from '../ui/Icon'

/**
 * Shown on every page while the backend runs on recorded answers (profile fake-models): the demo
 * is honest about what it is, and about the one thing it can analyse (R-12).
 */
export function RecordedBanner() {
  const { t } = useTranslation()
  const meta = useMeta()
  // The static replay has its own banner that says this and more (FR-85).
  if (isStaticReplay() || !meta.isSuccess || meta.data.recordedDemo === null) {
    return null
  }
  return (
    // Opaque underneath, so the landing scene never shows through the banner.
    <div role="note" className="relative z-10 bg-background text-sm">
      <div className="border-b border-uncertain/30 bg-uncertain/10">
        <p className="mx-auto flex max-w-6xl items-start gap-2 px-(--gutter) py-2">
          <Icon name="info" className="mt-1 shrink-0 text-uncertain" />
          <span>
            <strong>{t('common.recorded')}</strong> {t('common.recordedExplain')}
          </span>
        </p>
      </div>
    </div>
  )
}
