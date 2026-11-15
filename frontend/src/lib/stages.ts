import type { Stage } from '../api/sse'

/**
 * The six steps the visitor sees, and the pipeline stages behind each (api.md §4.2). The API sends
 * an event when a stage has COMPLETED, so a step is done once the last stage of its group has been
 * reported, and the step after it is the one running.
 */
export const UI_STAGES = [
  'reading',
  'redacting',
  'classifying',
  'searching',
  'analyzing',
  'verifying',
] as const

export type UiStage = (typeof UI_STAGES)[number]
export type StepState = 'done' | 'active' | 'waiting'

const GROUPS: Record<UiStage, readonly Stage[]> = {
  reading: ['INTAKE', 'OCR'],
  redacting: ['REDACT'],
  classifying: ['CLASSIFY'],
  searching: ['RETRIEVE', 'RULES'],
  analyzing: ['RISK'],
  verifying: ['VERIFY', 'CORRECT', 'COMPOSE'],
}

const ORDER: readonly Stage[] = [
  'INTAKE',
  'OCR',
  'REDACT',
  'CLASSIFY',
  'RETRIEVE',
  'RULES',
  'RISK',
  'VERIFY',
  'CORRECT',
  'COMPOSE',
  'COMPLETE',
]

/**
 * The state of each step given the stages reported so far. Text and text-layer PDFs never report
 * OCR, so for them INTAKE alone finishes the first step (`ocrExpected` false).
 */
export function stepStates(
  seen: readonly Stage[],
  options: { ocrExpected: boolean; finished: boolean },
): StepState[] {
  if (options.finished) {
    return UI_STAGES.map(() => 'done')
  }
  const highest = seen.reduce((max, stage) => Math.max(max, ORDER.indexOf(stage)), -1)
  const states: StepState[] = UI_STAGES.map((step) => {
    const needed = GROUPS[step].filter((stage) => options.ocrExpected || stage !== 'OCR')
    const last = needed.at(-1)
    return last !== undefined && highest >= ORDER.indexOf(last) ? 'done' : 'waiting'
  })
  const firstOpen = states.indexOf('waiting')
  if (firstOpen >= 0) {
    states[firstOpen] = 'active'
  }
  return states
}

/** The overall progress to show, from the API's own percentage. */
export function clampProgress(progress: number): number {
  return Math.min(100, Math.max(0, Math.round(progress)))
}
