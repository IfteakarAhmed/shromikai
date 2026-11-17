import { useTranslation } from 'react-i18next'
import { warningText } from '../../lib/warnings'
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

/**
 * The six steps of the analysis as a lit timeline, driven by the real stages the API reports
 * (FR-58, FR-69): done steps glow, the running one pulses and says in a plain sentence what is
 * happening; the only number besides the step numbers is the API's own percentage.
 */
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

        <ol className="progress-rail mt-6">
          {UI_STAGES.map((step, index) => {
            const state = states[index] ?? 'waiting'
            return (
              <li
                key={step}
                data-stage={step}
                data-state={state}
                aria-current={state === 'active' ? 'step' : undefined}
                className="relative flex gap-4 pb-6 last:pb-0"
              >
                <span
                  className={cx(
                    'stage-node relative z-10 flex size-10 shrink-0 items-center justify-center rounded-full border-2',
                    state === 'done' && 'border-verified bg-verified text-background',
                    state === 'active' && 'border-primary bg-background text-primary',
                    state === 'waiting' && 'border-border bg-background text-muted-foreground',
                  )}
                >
                  {state === 'done' ? (
                    <Icon name="check" size={18} />
                  ) : state === 'active' ? (
                    <Icon name="loader" size={18} className="animate-spin" />
                  ) : (
                    <span aria-hidden="true" className="font-ui">
                      {formatNumber(index + 1, language)}
                    </span>
                  )}
                </span>
                <div className="min-w-0 flex-1 pt-1.5">
                  <p
                    className={cx(
                      'flex flex-wrap items-baseline justify-between gap-x-3 font-semibold',
                      state === 'waiting' && 'text-muted-foreground',
                    )}
                  >
                    <span>{t(`progress.steps.${step}`)}</span>
                    <span className="text-sm font-normal text-muted-foreground">
                      {t(
                        state === 'done'
                          ? 'progress.stateDone'
                          : state === 'active'
                            ? 'progress.stateActive'
                            : 'progress.stateWaiting',
                      )}
                    </span>
                  </p>
                  {state === 'active' && (
                    // The live region below announces it; this copy is for the eye.
                    <p aria-hidden="true" className="stage-sentence mt-1 text-primary">
                      {t(`progress.sentences.${step}`)}
                    </p>
                  )}
                </div>
              </li>
            )
          })}
        </ol>
        <p role="status" className="sr-only">
          {active === undefined ? '' : t(`progress.sentences.${active}`)}
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
              key={`${warning.code}-${warningText(t, warning)}`}
              className="rounded-lg border border-uncertain/30 bg-uncertain/5 px-4 py-3"
            >
              <strong>{t('progress.warning')}: </strong>
              {warningText(t, warning)}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
