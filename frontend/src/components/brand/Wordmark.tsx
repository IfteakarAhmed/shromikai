import { useTranslation } from 'react-i18next'
import { cx } from '../../lib/cx'

/**
 * The product name in the display face, as live text so it follows the locale (শ্রমিকAI or
 * ShromikAI); a trailing "AI" takes the brand colour, as in the lockup files (ADR-0028).
 */
export function Wordmark({ className }: { className?: string }) {
  const { t } = useTranslation()
  const name = t('app.name')
  const stem = name.endsWith('AI') ? name.slice(0, -2) : name
  return (
    <span className={cx('font-display font-normal leading-none', className)}>
      {stem}
      {stem !== name && <span className="text-primary">AI</span>}
    </span>
  )
}
