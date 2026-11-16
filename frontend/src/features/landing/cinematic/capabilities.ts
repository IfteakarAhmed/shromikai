import type { Capabilities } from './tier'

interface NetworkInformation {
  effectiveType?: string
  saveData?: boolean
}

function context(kind: 'webgl2' | 'webgl'): boolean {
  try {
    const canvas = document.createElement('canvas')
    const gl = canvas.getContext(kind) as WebGLRenderingContext | null
    gl?.getExtension('WEBGL_lose_context')?.loseContext()
    return gl !== null
  } catch {
    return false
  }
}

/**
 * What this browser can do, read once for the landing tier (FR-68, FR-76). Values the browser does
 * not report stay undefined: `deviceMemory` and `connection` exist only in Chromium.
 */
export function readCapabilities(preferences: {
  reducedMotion: boolean
  lowBandwidth: boolean
}): Capabilities {
  const webgl2 = context('webgl2')
  const webgl = webgl2 || context('webgl')
  const nav = navigator as Navigator & {
    deviceMemory?: number
    connection?: NetworkInformation
  }
  const connection = nav.connection
  return {
    webgl,
    webgl2,
    hardwareConcurrency:
      typeof nav.hardwareConcurrency === 'number' ? nav.hardwareConcurrency : undefined,
    deviceMemory: typeof nav.deviceMemory === 'number' ? nav.deviceMemory : undefined,
    width: window.innerWidth,
    saveData: connection?.saveData === true,
    effectiveType:
      typeof connection?.effectiveType === 'string' ? connection.effectiveType : undefined,
    reducedMotion: preferences.reducedMotion,
    lowBandwidth: preferences.lowBandwidth,
  }
}
