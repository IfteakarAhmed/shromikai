/**
 * The scroll timeline of the cinematic landing (FR-68). Native scrolling drives it: the page is six
 * ordinary sections, and the timeline value `t` (0 to 1) is where a probe line in the viewport sits
 * among them, so the middle of chapter i is always `(i + 0.5) / 6`, whatever the section heights.
 * From `t` come the camera pose and the effect "beats" (0 to 1 each). Everything here is pure, so
 * the mapping is tested without a GPU.
 */
export const CHAPTERS = ['hero', 'privacy', 'search', 'verify', 'invented', 'finale'] as const
export type Chapter = (typeof CHAPTERS)[number]

export type Vec3 = readonly [number, number, number]

export interface CameraPose {
  position: Vec3
  target: Vec3
}

/** Effect progress, each 0 to 1 and never decreasing while the reader scrolls down. */
export interface Beats {
  /** Chapter 2: personal details on the sheet turn into solid bars. */
  redact: number
  /** Chapter 2: the shield ring gathers from particles. */
  shield: number
  /** Chapter 3: light pulses run through the law lattice to the matched sections. */
  search: number
  /** Chapter 4: the three check rings lock, one after another. */
  rings: readonly [number, number, number]
  /** Chapter 4: the emerald seal stamps, with its shockwave. */
  seal: number
  /** Chapter 5: cracks spread over the coral card. */
  crack: number
  /** Chapter 5: the coral card breaks into particles. */
  dissolve: number
  /** Chapter 6: the pull back to the opening composition. */
  finale: number
}

const COUNT = CHAPTERS.length
const WIDTH = 1 / COUNT
const clamp01 = (value: number) => Math.min(1, Math.max(0, value))
const centre = (index: number) => (index + 0.5) * WIDTH

/**
 * Where the probe (a y position in page pixels, usually the middle of the viewport) sits among the
 * sections: `starts` are their top edges in page pixels, `end` is the bottom of the last one.
 */
export function timelineFromScroll(probe: number, starts: readonly number[], end: number): number {
  const count = starts.length
  const first = starts[0]
  if (count === 0 || first === undefined) {
    return 0
  }
  if (probe <= first) {
    return 0
  }
  if (probe >= end) {
    return 1
  }
  let index = 0
  for (let i = 0; i < count; i += 1) {
    const start = starts[i]
    if (start !== undefined && start <= probe) {
      index = i
    }
  }
  const start = starts[index] ?? first
  const next = starts[index + 1] ?? end
  const local = next > start ? (probe - start) / (next - start) : 1
  return clamp01((index + clamp01(local)) / count)
}

export function chapterAt(t: number): { index: number; chapter: Chapter; local: number } {
  const scaled = clamp01(t) * COUNT
  const index = Math.min(COUNT - 1, Math.floor(scaled))
  return { index, chapter: CHAPTERS[index] ?? 'hero', local: scaled - index }
}

/** A clamped smoothstep from `from` to `to`. */
export function ramp(x: number, from: number, to: number): number {
  const u = clamp01((x - from) / (to - from))
  return u * u * (3 - 2 * u)
}

export function beatsAt(t: number): Beats {
  const x = clamp01(t)
  const c1 = centre(1)
  const c2 = centre(2)
  const c3 = centre(3)
  const c4 = centre(4)
  const c5 = centre(5)
  const ring = (k: number) =>
    ramp(x, c3 - 0.75 * WIDTH + k * 0.18 * WIDTH, c3 - 0.45 * WIDTH + k * 0.18 * WIDTH)
  return {
    redact: ramp(x, c1 - 0.7 * WIDTH, c1 - 0.1 * WIDTH),
    shield: ramp(x, c1 - 0.5 * WIDTH, c1),
    search: ramp(x, c2 - 0.6 * WIDTH, c2),
    rings: [ring(0), ring(1), ring(2)],
    seal: ramp(x, c3 - 0.08 * WIDTH, c3),
    crack: ramp(x, c4 - 0.7 * WIDTH, c4 - 0.3 * WIDTH),
    dissolve: ramp(x, c4 - 0.45 * WIDTH, c4),
    finale: ramp(x, c5 - 0.4 * WIDTH, 1),
  }
}

/** Where the stages of the scene stand, shared by the camera path and the scene. */
export const STAGES = {
  /** The floating contract sheets. */
  hero: [2.6, 0, 0] as Vec3,
  /** The wide lattice of law-section cards behind the sheets, seen when the camera pulls back. */
  lattice: [0.5, 0.6, -8] as Vec3,
  /** The check rings and the seal, in front of the lattice on the left. */
  verify: [-8, 0.4, -3] as Vec3,
  /** The coral and the emerald card, in front of the lattice on the right. */
  invented: [10.5, 0.4, -3.2] as Vec3,
} as const

/** One camera pose per chapter; the text sits on the left, so each subject sits right of centre. */
export const KEYFRAMES: readonly CameraPose[] = [
  { position: [-0.2, 0.4, 11], target: [-0.1, 0, 0] },
  { position: [1.4, 0.2, 8.8], target: [1.0, 0, 0.6] },
  { position: [-1, 2.2, 21], target: [0.5, 0.3, -4] },
  { position: [-6.2, 0.7, 5.8], target: [-10.2, 0.4, -3] },
  { position: [13.4, 0.8, 5.6], target: [7.2, 0.4, -3.2] },
  { position: [0.2, 0.9, 15], target: [0.6, 0, -1] },
]

const lerp3 = (a: Vec3, b: Vec3, s: number): Vec3 => [
  a[0] + (b[0] - a[0]) * s,
  a[1] + (b[1] - a[1]) * s,
  a[2] + (b[2] - a[2]) * s,
]

/**
 * The camera at `t`: it stands still around the middle of each chapter and travels between them
 * with an eased move. Before the first middle and after the last it holds the end poses.
 */
export function cameraAt(t: number): CameraPose {
  const x = clamp01(t)
  const first = KEYFRAMES[0]
  const last = KEYFRAMES[KEYFRAMES.length - 1]
  if (first === undefined || last === undefined) {
    throw new Error('no camera keyframes')
  }
  if (x <= centre(0)) {
    return first
  }
  if (x >= centre(COUNT - 1)) {
    return last
  }
  const index = Math.min(COUNT - 2, Math.floor((x - centre(0)) / WIDTH))
  const from = KEYFRAMES[index] ?? first
  const to = KEYFRAMES[index + 1] ?? last
  const s = ramp((x - centre(index)) / WIDTH, 0.1, 0.9)
  return {
    position: lerp3(from.position, to.position, s),
    target: lerp3(from.target, to.target, s),
  }
}
