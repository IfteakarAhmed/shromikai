import { describe, expect, it } from 'vitest'
import { INITIAL_QUALITY, nextQuality, qualitySettings, type QualityState } from './quality'

function run(state: QualityState, frameMs: number, frames: number): QualityState {
  let current = state
  for (let i = 0; i < frames; i += 1) {
    current = nextQuality(current, frameMs)
  }
  return current
}

describe('nextQuality', () => {
  it('FR-68 keeps full quality at 60 frames a second', () => {
    expect(run(INITIAL_QUALITY, 16.7, 600).level).toBe(3)
  })

  it('FR-68 drops one level when frames stay slow', () => {
    const after = run(INITIAL_QUALITY, 40, 120)
    expect(after.level).toBe(2)
  })

  it('FR-68 keeps dropping while frames stay slow, and stops at the lowest level', () => {
    expect(run(INITIAL_QUALITY, 60, 2000).level).toBe(0)
  })

  it('FR-68 ignores a few slow frames (a garbage collection, a tab switch)', () => {
    let state = INITIAL_QUALITY
    for (let i = 0; i < 20; i += 1) {
      state = run(state, 16.7, 30)
      state = run(state, 45, 3)
    }
    expect(state.level).toBe(3)
  })

  it('FR-68 ignores single frames longer than a quarter second (the tab was in the background)', () => {
    expect(run(INITIAL_QUALITY, 900, 50).level).toBe(3)
  })

  it('FR-68 never raises quality again in the same session', () => {
    const lowered = run(INITIAL_QUALITY, 60, 200)
    expect(lowered.level).toBeLessThan(3)
    expect(run(lowered, 8, 5000).level).toBe(lowered.level)
  })
})

describe('qualitySettings', () => {
  it('FR-68 gives fewer particles, then no bloom, then a lower pixel ratio, level by level', () => {
    const levels = ([3, 2, 1, 0] as const).map(qualitySettings)
    expect(levels[0]).toEqual({ particles: 1, bloom: true, maxPixelRatio: 1.5 })
    for (let i = 1; i < levels.length; i += 1) {
      const better = levels[i - 1]
      const worse = levels[i]
      if (better === undefined || worse === undefined) {
        throw new Error('missing level')
      }
      expect(worse.particles).toBeLessThanOrEqual(better.particles)
      expect(worse.maxPixelRatio).toBeLessThanOrEqual(better.maxPixelRatio)
    }
    expect(levels[2]?.bloom).toBe(false)
    expect(levels[3]?.maxPixelRatio).toBe(1)
  })
})
