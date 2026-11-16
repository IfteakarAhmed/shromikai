import { useTranslation } from 'react-i18next'
import { cx } from '../../lib/cx'
import { usePreferences } from '../../state/preferencesContext'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'

/**
 * Low-bandwidth mode (FR-76): no 3D, no charts, flat surfaces. It is remembered per device and
 * announced as a switch. In the header it is compact (icon and state; the words are the
 * accessible name and the tooltip), so the header never wraps; the phone menu shows the words.
 */
export function LowBandwidthToggle({ compact = true }: { compact?: boolean }) {
  const { t } = useTranslation()
  const { lowBandwidth, setLowBandwidth } = usePreferences()
  const state = t(lowBandwidth ? 'header.on' : 'header.off')
  return (
    <Button
      variant="outline"
      role="switch"
      aria-checked={lowBandwidth}
      title={`${t('header.lowBandwidth')}: ${state}`}
      className={cx(
        'whitespace-nowrap',
        lowBandwidth && 'border-primary/60 bg-primary/15 text-primary',
      )}
      onClick={() => {
        setLowBandwidth(!lowBandwidth)
      }}
    >
      <Icon name="gauge" />
      <span className={compact ? 'sr-only' : undefined}>{t('header.lowBandwidth')}</span>
      <span className="font-ui font-bold" aria-hidden="true">
        {state}
      </span>
    </Button>
  )
}
