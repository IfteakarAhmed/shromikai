import { describe, expect, it } from 'vitest'
import {
  CHAPTERS,
  KEYFRAMES,
  beatsAt,
  cameraAt,
  chapterAt,
  ramp,
  timelineFromScroll,
  type Vec3,
} from './timeline'

const distance = (a: Vec3, b: Vec3) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2])

describe('timelineFromScroll', () => {
  // Six sections of 1000 px each, starting at 100 (under the header); the page ends at 6100.
  const starts = [100, 1100, 2100, 3100, 4100, 5100]
  const end = 6100

  it('FR-68 is 0 before the first chapter and 1 after the last', () => {
    expect(timelineFromScroll(0, starts, end)).toBe(0)
    expect(timelineFromScroll(100, starts, end)).toBe(0)
    expect(timelineFromScroll(9000, starts, end)).toBe(1)
  })

  it('FR-68 puts the middle of chapter i at (i + 0.5) / 6, whatever the section heights', () => {
    expect(timelineFromScroll(600, starts, end)).toBeCloseTo(0.5 / 6)
    expect(timelineFromScroll(3600, starts, end)).toBeCloseTo(3.5 / 6)
    const uneven = [0, 500, 2500, 2600, 4000, 4100]
    expect(timelineFromScroll(1500, uneven, 5000)).toBeCloseTo(1.5 / 6)
    expect(timelineFromScroll(2550, uneven, 5000)).toBeCloseTo(2.5 / 6)
  })

  it('FR-68 never goes backwards while the reader scrolls down', () => {
    let last = -1
    for (let y = 0; y <= 6500; y += 37) {
      const t = timelineFromScroll(y, starts, end)
      expect(t).toBeGreaterThanOrEqual(last)
      last = t
    }
  })

  it('FR-68 is 0 when there are no sections to measure', () => {
    expect(timelineFromScroll(500, [], 0)).toBe(0)
  })
})

describe('chapterAt', () => {
  it('FR-68 names the six chapters in order and gives the progress inside each', () => {
    expect(CHAPTERS).toHaveLength(6)
    expect(chapterAt(0)).toEqual({ index: 0, chapter: 'hero', local: 0 })
    expect(chapterAt(2.5 / 6).chapter).toBe('search')
    expect(chapterAt(2.5 / 6).local).toBeCloseTo(0.5)
    expect(chapterAt(1)).toEqual({ index: 5, chapter: 'finale', local: 1 })
  })

  it('FR-68 clamps values outside 0 to 1', () => {
    expect(chapterAt(-3).index).toBe(0)
    expect(chapterAt(7).index).toBe(5)
  })
})

describe('ramp', () => {
  it('FR-68 is a clamped smoothstep', () => {
    expect(ramp(0, 0.2, 0.4)).toBe(0)
    expect(ramp(0.3, 0.2, 0.4)).toBeCloseTo(0.5)
    expect(ramp(1, 0.2, 0.4)).toBe(1)
  })
})

describe('beatsAt', () => {
  const centre = (index: number) => (index + 0.5) / 6

  it('FR-68 starts with every effect at rest', () => {
    const beats = beatsAt(0)
    expect(beats.redact).toBe(0)
    expect(beats.shield).toBe(0)
    expect(beats.search).toBe(0)
    expect(beats.rings).toEqual([0, 0, 0])
    expect(beats.seal).toBe(0)
    expect(beats.crack).toBe(0)
    expect(beats.dissolve).toBe(0)
    expect(beats.finale).toBe(0)
  })

  it('FR-68 finishes each chapter effect by the middle of its chapter', () => {
    expect(beatsAt(centre(1)).redact).toBe(1)
    expect(beatsAt(centre(1)).shield).toBe(1)
    expect(beatsAt(centre(2)).search).toBe(1)
    expect(beatsAt(centre(3)).rings).toEqual([1, 1, 1])
    expect(beatsAt(centre(3)).seal).toBe(1)
    expect(beatsAt(centre(4)).crack).toBe(1)
    expect(beatsAt(centre(4)).dissolve).toBe(1)
    expect(beatsAt(1).finale).toBe(1)
  })

  it('FR-68 locks the three check rings in sequence, before the seal stamps', () => {
    const early = beatsAt(centre(3) - 0.06)
    expect(early.rings[0]).toBeGreaterThan(early.rings[1])
    expect(early.rings[1]).toBeGreaterThanOrEqual(early.rings[2])
    expect(early.seal).toBe(0)
  })

  it('FR-68 cracks the invented card before it dissolves', () => {
    const t = centre(4) - 0.05
    expect(beatsAt(t).crack).toBeGreaterThan(beatsAt(t).dissolve)
  })

  it('FR-68 keeps every beat between 0 and 1 and never rewinds while scrolling down', () => {
    let last = beatsAt(0)
    for (let t = 0; t <= 1.0001; t += 0.005) {
      const beats = beatsAt(t)
      const values = [
        beats.redact,
        beats.shield,
        beats.search,
        ...beats.rings,
        beats.seal,
        beats.crack,
        beats.dissolve,
        beats.finale,
      ]
      const previous = [
        last.redact,
        last.shield,
        last.search,
        ...last.rings,
        last.seal,
        last.crack,
        last.dissolve,
        last.finale,
      ]
      values.forEach((value, index) => {
        expect(value).toBeGreaterThanOrEqual(0)
        expect(value).toBeLessThanOrEqual(1)
        expect(value).toBeGreaterThanOrEqual(previous[index] ?? 0)
      })
      last = beats
    }
  })
})

describe('cameraAt', () => {
  it('FR-68 has one keyframe per chapter and stands on it in the middle of the chapter', () => {
    expect(KEYFRAMES).toHaveLength(6)
    KEYFRAMES.forEach((pose, index) => {
      const at = cameraAt((index + 0.5) / 6)
      expect(distance(at.position, pose.position)).toBeLessThan(1e-6)
      expect(distance(at.target, pose.target)).toBeLessThan(1e-6)
    })
  })

  it('FR-68 holds the first and last poses at the ends', () => {
    expect(cameraAt(0)).toEqual(KEYFRAMES[0])
    expect(cameraAt(1)).toEqual(KEYFRAMES[5])
  })

  it('FR-68 ends by pulling back to the hero composition', () => {
    const hero = KEYFRAMES[0]
    const finale = KEYFRAMES[5]
    if (hero === undefined || finale === undefined) {
      throw new Error('missing keyframes')
    }
    expect(distance(hero.target, finale.target)).toBeLessThan(2.5)
    expect(distance(finale.position, finale.target)).toBeGreaterThan(
      distance(hero.position, hero.target),
    )
  })

  it('FR-68 moves smoothly: no jump larger than a small step between close scroll positions', () => {
    for (let t = 0; t < 1; t += 0.002) {
      const a = cameraAt(t)
      const b = cameraAt(t + 0.002)
      expect(distance(a.position, b.position)).toBeLessThan(0.5)
    }
  })
})
