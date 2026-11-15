import { useTranslation } from 'react-i18next'
import { usePreferences } from '../../state/preferencesContext'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'

/**
 * Low-bandwidth mode (FR-76): no 3D hero, no charts, flat surfaces. It is remembered per device and
 * announced as a switch.
 */
export function LowBandwidthToggle() {
  const { t } = useTranslation()
  const { lowBandwidth, setLowBandwidth } = usePreferences()
  return (
    <Button
      variant="outline"
      role="switch"
      aria-checked={lowBandwidth}
      onClick={() => {
        setLowBandwidth(!lowBandwidth)
      }}
    >
      <Icon name="gauge" />
      <span className="hidden xl:inline">{t('header.lowBandwidth')}</span>
      <span className="sr-only xl:hidden">{t('header.lowBandwidth')}</span>
      <span className="font-bold" aria-hidden="true">
        {t(lowBandwidth ? 'header.on' : 'header.off')}
      </span>
    </Button>
  )
}
