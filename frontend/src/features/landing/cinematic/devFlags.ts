import type { QualityLevel } from './quality'

/**
 * Development-only switches for diagnosing the cinematic scene on a real GPU (FR-68):
 * `?bloom=off`, `?shafts=off`, `?particles=off`, `?sheets=off` turn one layer off, `?quality=0..3`
 * pins the quality level (no adaptive changes), `?perf=1` shows the frame-time overlay. A
 * production build ignores them all.
 */
export interface DevFlags {
  bloom: boolean
  shafts: boolean
  particles: boolean
  sheets: boolean
  quality: QualityLevel | null
  perf: boolean
}

export const DEFAULT_FLAGS: DevFlags = {
  bloom: true,
  shafts: true,
  particles: true,
  sheets: true,
  quality: null,
  perf: false,
}

export function parseDevFlags(search: string, dev: boolean): DevFlags {
  if (!dev) {
    return DEFAULT_FLAGS
  }
  const params = new URLSearchParams(search)
  const on = (name: string) => params.get(name) !== 'off'
  const quality = params.get('quality')
  return {
    bloom: on('bloom'),
    shafts: on('shafts'),
    particles: on('particles'),
    sheets: on('sheets'),
    quality: quality !== null && /^[0-3]$/.test(quality) ? (Number(quality) as QualityLevel) : null,
    perf: params.get('perf') === '1',
  }
}
