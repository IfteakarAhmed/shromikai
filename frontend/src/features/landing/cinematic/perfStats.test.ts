import { describe, expect, it } from 'vitest'
import { frameStats } from './perfStats'

describe('frameStats', () => {
  it('FR-68 reports 60 frames a second for steady 16.7 ms frames', () => {
    const stats = frameStats(Array.from({ length: 120 }, () => 1000 / 60))
    expect(stats.fps).toBeCloseTo(60, 5)
    expect(stats.averageMs).toBeCloseTo(16.667, 2)
    expect(stats.p95Ms).toBeCloseTo(16.667, 2)
  })

  it('FR-68 shows slow frames in the 95th percentile while the average hides them', () => {
    const samples = [
      ...Array.from({ length: 90 }, () => 10),
      ...Array.from({ length: 10 }, () => 50),
    ]
    const stats = frameStats(samples)
    expect(stats.averageMs).toBeCloseTo(14, 5)
    expect(stats.p95Ms).toBe(50)
    expect(stats.fps).toBeCloseTo(1000 / 14, 5)
  })

  it('FR-68 reports zeros when there are no samples yet', () => {
    expect(frameStats([])).toEqual({ fps: 0, averageMs: 0, p95Ms: 0 })
  })
})
