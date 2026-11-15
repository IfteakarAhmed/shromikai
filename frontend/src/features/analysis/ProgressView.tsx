import { useTranslation } from 'react-i18next'
import { cx } from '../../lib/cx'
import { formatNumber } from '../../lib/format'
import { clampProgress, UI_STAGES, type StepState } from '../../lib/stages'
import type { Language } from '../../i18n'
import { Icon } from '../../components/ui/Icon'
import { GlassCard } from '../../components/ui/GlassCard'

interface ProgressViewProps {
  states: readonly StepState[]
  progress: number
  warnings: readonly { code: string; message: string }[]
  polling: boolean
}

/** The six steps of the analysis, driven by the real stages the API reports (FR-58, FR-69). */
export function ProgressView({ states, progress, warnings, polling }: ProgressViewProps) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const percent = clampProgress(progress)
  const activeIndex = states.indexOf('active')
  const active = activeIndex >= 0 ? UI_STAGES[activeIndex] : undefined

  return (
    <div className="mx-auto max-w-2xl px-(--gutter) py-10">
      <h1 className="text-3xl font-bold">{t('progress.title')}</h1>
      <p className="mt-2 text-muted-foreground">{t('progress.subtitle')}</p>

      <GlassCard className="mt-6 p-5">
        <div
          role="progressbar"
          aria-label={t('progress.title')}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
          aria-valuetext={t('progress.percent', { percent: formatNumber(percent, language) })}
          className="relative h-2 overflow-hidden rounded-full bg-secondary"
        >
          <div
            className="h-full rounded-full bg-primary transition-[width] duration-500"
            style={{ width: `${percent}%` }}
          />
        </div>
        <p className="mt-2 text-sm text-muted-foreground">
          {t('progress.percent', { percent: formatNumber(percent, language) })}
        </p>

        <ol className="mt-5 space-y-3">
          {UI_STAGES.map((step, index) => {
            const state = states[index] ?? 'waiting'
            return (
              <li
                key={step}
                aria-current={state === 'active' ? 'step' : undefined}
                className={cx(
                  'flex items-center gap-3 rounded-lg border px-4 py-3 transition-colors',
                  state === 'active' && 'border-primary/60 bg-primary/10',
                  state === 'done' && 'border-verified/30 bg-verified/5',
                  state === 'waiting' && 'border-border opacity-70',
                )}
              >
                <span
                  className={cx(
                    'flex size-8 shrink-0 items-center justify-center rounded-full border',
                    state === 'done' && 'border-verified bg-verified text-background',
                    state === 'active' && 'border-primary text-primary',
                    state === 'waiting' && 'border-border text-muted-foreground',
                  )}
                >
                  {state === 'done' ? (
                    <Icon name="check" size={16} />
                  ) : state === 'active' ? (
                    <Icon name="loader" size={16} className="animate-spin" />
                  ) : (
                    <span aria-hidden="true">{formatNumber(index + 1, language)}</span>
                  )}
                </span>
                <span className="flex-1 font-medium">{t(`progress.steps.${step}`)}</span>
                <span className="text-sm text-muted-foreground">
                  {t(
                    state === 'done'
                      ? 'progress.stateDone'
                      : state === 'active'
                        ? 'progress.stateActive'
                        : 'progress.stateWaiting',
                  )}
                </span>
              </li>
            )
          })}
        </ol>
        <p role="status" className="sr-only">
          {active === undefined ? '' : t(`progress.steps.${active}`)}
        </p>
      </GlassCard>

      {polling && (
        <p
          role="note"
          className="mt-4 rounded-lg border border-uncertain/30 bg-uncertain/5 px-4 py-3"
        >
          {t('progress.polling')}
        </p>
      )}
      {warnings.length > 0 && (
        <ul className="mt-4 space-y-2">
          {warnings.map((warning) => (
            <li
              key={`${warning.code}-${warning.message}`}
              className="rounded-lg border border-uncertain/30 bg-uncertain/5 px-4 py-3"
            >
              <strong>{t('progress.warning')}: </strong>
              {warning.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
