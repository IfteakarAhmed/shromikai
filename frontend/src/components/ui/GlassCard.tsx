import { useRef, type HTMLAttributes, type PointerEvent } from 'react'
import { useMediaQuery } from '../../features/landing/useCapabilities'
import { cx } from '../../lib/cx'
import { maxTiltDegrees, tiltAngles, tiltCapable } from '../../lib/tilt'
import { usePreferencesOptional } from '../../state/preferencesContext'

interface GlassCardProps extends HTMLAttributes<HTMLElement> {
  as?: 'article' | 'aside' | 'div' | 'section' | 'li'
  interactive?: boolean
  /** Rises into place on scroll (a vertical move only; off with reduced motion or low bandwidth). */
  reveal?: boolean
  /**
   * `tilt` (default) floats: it leans towards a fine pointer and its `lift-1` and `lift-2` children
   * stand out in front. `still` has the same look but never moves: for forms and big containers.
   */
  depth?: 'tilt' | 'still'
}

const round = (value: number, digits: number) => String(Number(value.toFixed(digits)))

const CONTROLS = 'a, button, summary, input, select, textarea, label, [role="button"]'

/**
 * A glass card with depth (C2b, CSS 3D only). The glass and the glow live on two hidden layers
 * behind the content, so the blur never sits on the element that tilts (a blur would flatten the
 * 3D scene). With a fine pointer, motion allowed and low-bandwidth mode off, the card leans
 * towards the pointer and children marked `lift-1` or `lift-2` float in front of the glass;
 * otherwise it stays flat and keeps the same look.
 */
export function GlassCard({
  as: Element = 'div',
  interactive = false,
  reveal = true,
  depth = 'tilt',
  className,
  children,
  onPointerMove,
  onPointerLeave,
  ...props
}: GlassCardProps) {
  const lowBandwidth = usePreferencesOptional()?.lowBandwidth ?? false
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  const finePointer = useMediaQuery('(hover: hover) and (pointer: fine)')
  const capable = depth === 'tilt' && tiltCapable({ finePointer, reducedMotion, lowBandwidth })
  const ref = useRef<HTMLElement>(null)
  const frame = useRef(0)
  const Tag = Element as 'div'

  function move(event: PointerEvent<HTMLDivElement>) {
    onPointerMove?.(event)
    const element = ref.current
    if (!capable || element === null || event.pointerType === 'touch') {
      return
    }
    // Over a control the lean holds still, so the control never slides under the pointer
    // between pressing and releasing.
    if ((event.target as Element).closest(CONTROLS) !== null) {
      return
    }
    const rect = element.getBoundingClientRect()
    const box = {
      cx: rect.left + rect.width / 2,
      cy: rect.top + rect.height / 2,
      halfW: element.offsetWidth / 2,
      halfH: element.offsetHeight / 2,
    }
    const tilt = tiltAngles(
      event.clientX,
      event.clientY,
      box,
      maxTiltDegrees(element.offsetWidth, element.offsetHeight),
    )
    cancelAnimationFrame(frame.current)
    frame.current = requestAnimationFrame(() => {
      element.dataset['tilting'] = 'true'
      element.style.setProperty('--rx', `${round(tilt.rx, 2)}deg`)
      element.style.setProperty('--ry', `${round(tilt.ry, 2)}deg`)
      element.style.setProperty('--gx', round(tilt.gx, 3))
      element.style.setProperty('--gy', round(tilt.gy, 3))
    })
  }

  function leave(event: PointerEvent<HTMLDivElement>) {
    onPointerLeave?.(event)
    const element = ref.current
    if (element === null) {
      return
    }
    cancelAnimationFrame(frame.current)
    delete element.dataset['tilting']
    element.style.setProperty('--rx', '0deg')
    element.style.setProperty('--ry', '0deg')
  }

  return (
    <Tag
      ref={ref as React.Ref<HTMLDivElement>}
      className={cx(
        'depth-card rounded-2xl',
        interactive && 'glass-interactive',
        reveal && 'reveal',
        className,
      )}
      data-tilt={capable ? 'on' : undefined}
      onPointerMove={move}
      onPointerLeave={leave}
      {...props}
    >
      <span aria-hidden="true" className="depth-glass" />
      <span aria-hidden="true" className="depth-glow" />
      {children}
    </Tag>
  )
}
