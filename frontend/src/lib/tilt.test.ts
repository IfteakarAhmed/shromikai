import { describe, expect, it } from 'vitest'
import { maxTiltDegrees, tiltAngles, tiltCapable, type TiltBox } from './tilt'

const BOX: TiltBox = { cx: 150, cy: 100, halfW: 150, halfH: 100 }

describe('tiltAngles', () => {
  it('FR-68 is flat with the pointer at the centre', () => {
    expect(tiltAngles(150, 100, BOX, 6)).toEqual({ rx: 0, ry: 0, gx: 0.5, gy: 0.5 })
  })

  it('FR-68 presses the edge under the pointer: right edge gives a positive rotateY', () => {
    const tilt = tiltAngles(300, 100, BOX, 6)
    expect(tilt.ry).toBeCloseTo(6)
    expect(tilt.rx).toBeCloseTo(0)
    expect(tilt.gx).toBe(1)
  })

  it('FR-68 the top edge gives a positive rotateX, the bottom a negative one', () => {
    expect(tiltAngles(150, 0, BOX, 6).rx).toBeCloseTo(6)
    expect(tiltAngles(150, 200, BOX, 6).rx).toBeCloseTo(-6)
  })

  it('FR-68 scales with the distance from the centre', () => {
    expect(tiltAngles(225, 100, BOX, 6).ry).toBeCloseTo(3)
  })

  it('FR-68 never exceeds the maximum, even with the pointer outside the card', () => {
    const tilt = tiltAngles(900, -500, BOX, 6)
    expect(Math.abs(tilt.ry)).toBeLessThanOrEqual(6)
    expect(Math.abs(tilt.rx)).toBeLessThanOrEqual(6)
    expect(tilt.gx).toBe(1)
    expect(tilt.gy).toBe(0)
  })

  it('FR-68 survives a card with no size (it is flat, not NaN)', () => {
    const tilt = tiltAngles(10, 10, { cx: 0, cy: 0, halfW: 0, halfH: 0 }, 6)
    expect(Number.isFinite(tilt.rx)).toBe(true)
    expect(Number.isFinite(tilt.ry)).toBe(true)
    expect(Number.isFinite(tilt.gx)).toBe(true)
  })
})

describe('maxTiltDegrees', () => {
  it('FR-68 gives small cards 6 degrees and big documents almost none', () => {
    expect(maxTiltDegrees(300, 200)).toBe(6)
    expect(maxTiltDegrees(320, 320)).toBe(6)
    expect(maxTiltDegrees(1120, 800)).toBeCloseTo(1.5)
    expect(maxTiltDegrees(2000, 3000)).toBeCloseTo(1.5)
  })

  it('FR-68 shrinks smoothly in between', () => {
    expect(maxTiltDegrees(800, 400)).toBeGreaterThan(maxTiltDegrees(1000, 400))
    expect(maxTiltDegrees(800, 400)).toBeLessThan(6)
    expect(maxTiltDegrees(800, 400)).toBeGreaterThan(1.5)
  })
})

describe('tiltCapable', () => {
  it('FR-76 tilts only with a fine pointer, motion allowed and low-bandwidth mode off', () => {
    expect(tiltCapable({ finePointer: true, reducedMotion: false, lowBandwidth: false })).toBe(true)
    expect(tiltCapable({ finePointer: false, reducedMotion: false, lowBandwidth: false })).toBe(
      false,
    )
    expect(tiltCapable({ finePointer: true, reducedMotion: true, lowBandwidth: false })).toBe(false)
    expect(tiltCapable({ finePointer: true, reducedMotion: false, lowBandwidth: true })).toBe(false)
  })
})
