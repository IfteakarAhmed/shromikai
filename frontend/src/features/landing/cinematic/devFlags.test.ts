import { describe, expect, it } from 'vitest'
import { DEFAULT_FLAGS, parseDevFlags } from './devFlags'

describe('parseDevFlags', () => {
  it('FR-68 keeps every layer on and quality adaptive by default', () => {
    expect(parseDevFlags('', true)).toEqual(DEFAULT_FLAGS)
    expect(parseDevFlags('?scene=cinematic', true)).toEqual(DEFAULT_FLAGS)
  })

  it('FR-68 turns single layers off for diagnosis', () => {
    expect(parseDevFlags('?bloom=off', true)).toEqual({ ...DEFAULT_FLAGS, bloom: false })
    expect(parseDevFlags('?shafts=off&particles=off&sheets=off', true)).toEqual({
      ...DEFAULT_FLAGS,
      shafts: false,
      particles: false,
      sheets: false,
    })
  })

  it('FR-68 pins a quality level from 0 to 3 and ignores anything else', () => {
    expect(parseDevFlags('?quality=3', true).quality).toBe(3)
    expect(parseDevFlags('?quality=0', true).quality).toBe(0)
    expect(parseDevFlags('?quality=7', true).quality).toBeNull()
    expect(parseDevFlags('?quality=high', true).quality).toBeNull()
  })

  it('FR-68 shows the frame-time overlay with perf=1', () => {
    expect(parseDevFlags('?perf=1', true).perf).toBe(true)
    expect(parseDevFlags('?perf=0', true).perf).toBe(false)
  })

  it('FR-68 ignores every flag in a production build', () => {
    expect(parseDevFlags('?bloom=off&shafts=off&quality=0&perf=1', false)).toEqual(DEFAULT_FLAGS)
  })
})
