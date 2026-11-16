import { useTranslation } from 'react-i18next'
import { Icon } from './Icon'

/** "This is not legal advice" (R-12, FR-77): on every result, memo and docs page. */
export function Disclaimer({ text }: { text?: string }) {
  const { t } = useTranslation()
  return (
    <aside
      role="note"
      className="flex items-start gap-2.5 rounded-xl border border-uncertain/30 bg-uncertain/5 px-4 py-3 shadow-[0_0_40px_-24px_var(--glow-uncertain)]"
    >
      <Icon name="info" className="mt-1 shrink-0 text-uncertain" />
      <p className="font-medium">{text ?? t('app.disclaimer')}</p>
    </aside>
  )
}
