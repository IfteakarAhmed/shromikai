import { describe, expect, it } from 'vitest'
import { damp } from './damp'

describe('damp', () => {
  it('FR-68 does not move when no time has passed', () => {
    expect(damp(0, 1, 6, 0)).toBe(0)
  })

  it('FR-68 approaches the target and arrives after a long time', () => {
    const step = damp(0, 1, 6, 1 / 60)
    expect(step).toBeGreaterThan(0)
    expect(step).toBeLessThan(1)
    expect(damp(0, 1, 6, 10)).toBeCloseTo(1, 6)
  })

  it('FR-68 is independent of frame rate: two half steps equal one full step', () => {
    const once = damp(0.2, 0.9, 4, 1 / 30)
    const twice = damp(damp(0.2, 0.9, 4, 1 / 60), 0.9, 4, 1 / 60)
    expect(twice).toBeCloseTo(once, 10)
  })

  it('FR-68 treats a negative time as no time (a clock that jumps back)', () => {
    expect(damp(0.4, 1, 6, -0.5)).toBe(0.4)
  })
})
