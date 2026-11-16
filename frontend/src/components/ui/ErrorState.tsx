import { useTranslation } from 'react-i18next'
import { describeError } from '../../lib/problems'
import type { Language } from '../../i18n'
import { Button } from './Button'
import { Icon } from './Icon'

interface ErrorStateProps {
  error: unknown
  onRetry?: () => void
}

/** A failure in plain words, with a way to try again (FR-76). */
export function ErrorState({ error, onRetry }: ErrorStateProps) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  return (
    <div
      role="alert"
      className="glass-panel flex flex-col items-start gap-3 rounded-2xl border-rejected/40 p-5"
    >
      <p className="flex items-start gap-2 font-medium text-rejected">
        <Icon name="alert" className="mt-1 shrink-0" />
        <span>{describeError(error, t, language)}</span>
      </p>
      {onRetry !== undefined && (
        <Button variant="outline" onClick={onRetry}>
          <Icon name="refresh" />
          {t('common.retry')}
        </Button>
      )}
    </div>
  )
}
