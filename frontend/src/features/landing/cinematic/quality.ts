/**
 * Adaptive quality for the cinematic scene (ADR-0023). A moving average of frame times; when it
 * stays above the budget for a full window, quality drops one level: fewer particles first, then no
 * bloom, then a lower pixel ratio. It never rises again in the same session, so the scene does not
 * oscillate. Frames longer than a quarter second are ignored (the tab was hidden or the page was
 * busy loading), and short bursts of slow frames do not count.
 */
export type QualityLevel = 0 | 1 | 2 | 3

export interface QualityState {
  level: QualityLevel
  /** Exponential moving average of frame time in milliseconds. */
  averageMs: number
  /** Consecutive frames with the average above the budget. */
  slowFrames: number
}

export interface QualitySettings {
  /** Share of the full particle counts to draw, 0 to 1. */
  particles: number
  bloom: boolean
  maxPixelRatio: number
}

export const INITIAL_QUALITY: QualityState = { level: 3, averageMs: 16.7, slowFrames: 0 }

/** About 45 frames a second. */
const BUDGET_MS = 22
/** Frames the average must stay over budget before a drop (about 1.5 s at 40 fps). */
const WINDOW = 60
const SMOOTHING = 0.1
const IGNORE_ABOVE_MS = 250

export function nextQuality(state: QualityState, frameMs: number): QualityState {
  if (!(frameMs > 0) || frameMs > IGNORE_ABOVE_MS) {
    return state
  }
  const averageMs = state.averageMs + (frameMs - state.averageMs) * SMOOTHING
  if (averageMs <= BUDGET_MS) {
    return { level: state.level, averageMs, slowFrames: 0 }
  }
  const slowFrames = state.slowFrames + 1
  if (slowFrames < WINDOW || state.level === 0) {
    return { level: state.level, averageMs, slowFrames }
  }
  // Drop a level and give the new level a fresh window from the budget line.
  return { level: (state.level - 1) as QualityLevel, averageMs: BUDGET_MS, slowFrames: 0 }
}

const SETTINGS: Record<QualityLevel, QualitySettings> = {
  3: { particles: 1, bloom: true, maxPixelRatio: 1.5 },
  2: { particles: 0.5, bloom: true, maxPixelRatio: 1.5 },
  1: { particles: 0.35, bloom: false, maxPixelRatio: 1.25 },
  0: { particles: 0.2, bloom: false, maxPixelRatio: 1 },
}

export function qualitySettings(level: QualityLevel): QualitySettings {
  return SETTINGS[level]
}
