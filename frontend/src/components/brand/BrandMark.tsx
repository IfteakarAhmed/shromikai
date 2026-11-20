import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { cx } from '../../lib/cx'
import {
  BADGE,
  CHECK,
  CHECK_WIDTH,
  GLYPH,
  RING,
  SHIELD,
  SMALL_CHECK,
  SMALL_CHECK_WIDTH,
} from './brandPaths'

type Props = {
  /** `full`: the শ্র seal with the verified badge; `small`: shield and check for 16 to 32 px. */
  variant?: 'full' | 'small'
  /**
   * A standalone mark is an image named in the current locale. Leave it unset when the visible
   * name sits beside the mark, so assistive technology does not read the name twice.
   */
  labelled?: boolean
  className?: string
}

/**
 * The ShromikAI mark (ADR-0028) as inline SVG in one ink, `currentColor`: the shield with the
 * conjunct, badge ring and check knocked out, so it shows whatever is behind it and follows the
 * theme's primary colour in dark and light alike.
 */
export function BrandMark({ variant = 'full', labelled = false, className }: Props) {
  const { t } = useTranslation()
  const maskId = `brand-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
  const knockout = {
    fill: 'none',
    stroke: '#000',
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
  } as const
  return (
    <svg
      viewBox="0 0 64 64"
      className={cx('shrink-0 text-primary', className)}
      {...(labelled
        ? { role: 'img', 'aria-label': t('brand.markAlt', { name: t('app.name') }) }
        : { 'aria-hidden': true, focusable: false })}
    >
      <defs>
        <mask id={maskId} maskUnits="userSpaceOnUse" x="0" y="0" width="64" height="64">
          <rect width="64" height="64" fill="#fff" />
          {variant === 'full' ? (
            <>
              <path d={GLYPH} fill="#000" />
              <path d={RING} fill="#000" />
              <path d={BADGE} fill="#fff" />
              <path d={CHECK} strokeWidth={CHECK_WIDTH} {...knockout} />
            </>
          ) : (
            <path d={SMALL_CHECK} strokeWidth={SMALL_CHECK_WIDTH} {...knockout} />
          )}
        </mask>
      </defs>
      <g fill="currentColor" mask={`url(#${maskId})`}>
        <path d={SHIELD} />
        {variant === 'full' && <path d={BADGE} />}
      </g>
    </svg>
  )
}
