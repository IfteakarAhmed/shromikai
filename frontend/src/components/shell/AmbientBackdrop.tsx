import { useMediaQuery } from '../../features/landing/useCapabilities'
import { usePreferences } from '../../state/preferencesContext'

/**
 * Soft glow orbs behind the app pages, in CSS only (C1). They float slowly; with reduced motion
 * they stay still, and in low-bandwidth mode there is nothing at all (FR-76).
 */
export function AmbientBackdrop() {
  const { lowBandwidth } = usePreferences()
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  if (lowBandwidth) {
    return null
  }
  return (
    <div
      aria-hidden="true"
      data-backdrop
      data-motion={reducedMotion ? 'still' : 'float'}
      className="ambient"
    >
      <span data-orb="1" />
      <span data-orb="2" />
      <span data-orb="3" />
    </div>
  )
}
