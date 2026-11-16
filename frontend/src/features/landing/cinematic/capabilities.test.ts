import { afterEach, describe, expect, it, vi } from 'vitest'
import { readCapabilities } from './capabilities'

function withContexts(kinds: readonly string[]) {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(((kind: string) =>
    kinds.includes(kind)
      ? { getExtension: () => null }
      : null) as typeof HTMLCanvasElement.prototype.getContext)
}

function withNavigator(values: Record<string, unknown>) {
  for (const [key, value] of Object.entries(values)) {
    Object.defineProperty(navigator, key, { value, configurable: true })
  }
}

afterEach(() => {
  vi.restoreAllMocks()
  withNavigator({ connection: undefined, deviceMemory: undefined, hardwareConcurrency: 4 })
})

describe('readCapabilities', () => {
  it('FR-68 reads WebGL, cores, memory, width and the connection from the browser', () => {
    withContexts(['webgl2', 'webgl'])
    withNavigator({
      hardwareConcurrency: 8,
      deviceMemory: 8,
      connection: { effectiveType: '4g', saveData: false },
    })
    window.innerWidth = 1440

    expect(readCapabilities({ reducedMotion: false, lowBandwidth: false })).toEqual({
      webgl: true,
      webgl2: true,
      hardwareConcurrency: 8,
      deviceMemory: 8,
      width: 1440,
      saveData: false,
      effectiveType: '4g',
      reducedMotion: false,
      lowBandwidth: false,
    })
  })

  it('FR-76 passes the visitor preferences and data saver through', () => {
    withContexts(['webgl'])
    withNavigator({ connection: { effectiveType: '3g', saveData: true } })
    window.innerWidth = 390

    const caps = readCapabilities({ reducedMotion: true, lowBandwidth: true })
    expect(caps.webgl).toBe(true)
    expect(caps.webgl2).toBe(false)
    expect(caps.saveData).toBe(true)
    expect(caps.effectiveType).toBe('3g')
    expect(caps.reducedMotion).toBe(true)
    expect(caps.lowBandwidth).toBe(true)
    expect(caps.width).toBe(390)
  })

  it('FR-68 reports unknown values as undefined when the browser does not tell', () => {
    withContexts([])
    withNavigator({ connection: undefined, deviceMemory: undefined })

    const caps = readCapabilities({ reducedMotion: false, lowBandwidth: false })
    expect(caps.webgl).toBe(false)
    expect(caps.deviceMemory).toBeUndefined()
    expect(caps.effectiveType).toBeUndefined()
    expect(caps.saveData).toBe(false)
  })

  it('FR-68 counts a browser that throws on getContext as having no WebGL', () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(readCapabilities({ reducedMotion: false, lowBandwidth: false }).webgl).toBe(false)
  })
})
