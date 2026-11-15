import { cx } from '../../lib/cx'

/** A loading placeholder; it is hidden from assistive technology (the page announces loading). */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cx('skeleton rounded-md', className)} />
}
