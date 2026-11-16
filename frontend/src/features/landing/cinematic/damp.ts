/**
 * Exponential damping towards a target, independent of frame rate: `lambda` is the rate per second,
 * so two half steps give the same value as one full step. A negative time counts as none.
 */
export function damp(current: number, target: number, lambda: number, seconds: number): number {
  if (seconds <= 0) {
    return current
  }
  return target + (current - target) * Math.exp(-lambda * seconds)
}
