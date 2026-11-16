/** Frame statistics for the development overlay (`?perf=1`). */
export interface FrameStats {
  fps: number
  averageMs: number
  p95Ms: number
}

export function frameStats(frameMs: readonly number[]): FrameStats {
  if (frameMs.length === 0) {
    return { fps: 0, averageMs: 0, p95Ms: 0 }
  }
  const averageMs = frameMs.reduce((sum, value) => sum + value, 0) / frameMs.length
  const sorted = [...frameMs].sort((a, b) => a - b)
  const p95Ms = sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.95) - 1)] ?? 0
  return { fps: averageMs > 0 ? 1000 / averageMs : 0, averageMs, p95Ms }
}
