import { describe, expect, it } from 'vitest'
import { optInTier, resolveTier, selectTier, type Capabilities } from './tier'

/** A fast desktop on a good connection: the only kind that gets the cinematic scene by itself. */
const DESKTOP: Capabilities = {
  webgl: true,
  webgl2: true,
  hardwareConcurrency: 8,
  deviceMemory: 8,
  width: 1280,
  saveData: false,
  effectiveType: '4g',
  reducedMotion: false,
  lowBandwidth: false,
}

const with_ = (changes: Partial<Capabilities>): Capabilities => ({ ...DESKTOP, ...changes })

describe('selectTier', () => {
  it('FR-68 gives the cinematic scene to a capable desktop on a fast connection', () => {
    expect(selectTier(DESKTOP)).toBe('cinematic')
  })

  it('FR-68 treats an unknown connection type and unknown memory as allowed (Safari, Firefox)', () => {
    expect(selectTier(with_({ effectiveType: undefined, deviceMemory: undefined }))).toBe(
      'cinematic',
    )
  })

  it.each([
    ['no WebGL2', { webgl2: false }],
    ['fewer than 4 cores', { hardwareConcurrency: 2 }],
    ['unknown core count', { hardwareConcurrency: undefined }],
    ['less than 4 GB of memory', { deviceMemory: 2 }],
    ['a window narrower than 1024', { width: 1023 }],
  ] as const)('FR-68 falls back to the standard scene with %s', (_label, changes) => {
    expect(selectTier(with_(changes))).toBe('standard')
  })

  it.each([
    ['no WebGL at all', { webgl: false, webgl2: false }],
    ['a phone width under 768', { width: 767 }],
    ['a 3g connection', { effectiveType: '3g' }],
    ['a slow-2g connection', { effectiveType: 'slow-2g' }],
    ['data saver on', { saveData: true }],
    ['reduced motion', { reducedMotion: true }],
    ['low-bandwidth mode', { lowBandwidth: true }],
  ] as const)('FR-76 defaults to the lite poster with %s', (_label, changes) => {
    expect(selectTier(with_(changes))).toBe('lite')
  })

  it('FR-76 keeps phones on lite even when the phone is fast', () => {
    expect(selectTier(with_({ width: 390, hardwareConcurrency: 8, deviceMemory: 8 }))).toBe('lite')
  })

  it('FR-68 gives a 768 to 1023 wide tablet the standard scene', () => {
    expect(selectTier(with_({ width: 768 }))).toBe('standard')
  })
})

describe('optInTier', () => {
  it('FR-68 offers nothing when the browser has no WebGL', () => {
    expect(optInTier(with_({ webgl: false, webgl2: false }))).toBeNull()
  })

  it('FR-68 lets a phone opt in to the standard scene', () => {
    expect(optInTier(with_({ width: 390, effectiveType: '3g', saveData: true }))).toBe('standard')
  })

  it('FR-68 lets a capable desktop that chose lite opt back in to the cinematic scene', () => {
    expect(optInTier(with_({ lowBandwidth: true, reducedMotion: true }))).toBe('cinematic')
  })

  it('FR-68 opts a WebGL1-only desktop in to the standard scene', () => {
    expect(optInTier(with_({ webgl2: false }))).toBe('standard')
  })
})

describe('resolveTier', () => {
  it('FR-68 follows the automatic choice by default', () => {
    expect(resolveTier(DESKTOP, 'auto')).toBe('cinematic')
    expect(resolveTier(with_({ width: 390 }), 'auto')).toBe('lite')
  })

  it('FR-76 always honours the switch to lite', () => {
    expect(resolveTier(DESKTOP, 'lite')).toBe('lite')
  })

  it('FR-68 turns the scene on when asked, and stays lite without WebGL', () => {
    expect(resolveTier(with_({ width: 390 }), 'scene')).toBe('standard')
    expect(resolveTier(with_({ lowBandwidth: true }), 'scene')).toBe('cinematic')
    expect(resolveTier(with_({ webgl: false, webgl2: false }), 'scene')).toBe('lite')
  })
})
