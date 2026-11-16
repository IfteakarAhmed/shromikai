/**
 * The maths of the pointer tilt on cards (C2b, CSS 3D depth). Pure, so the feel is tested without
 * a browser: the edge under the pointer is pressed away, cards tilt less the bigger they are, and
 * nothing tilts without a fine pointer, with reduced motion, or in low-bandwidth mode.
 */
export interface TiltBox {
  /** Centre of the card on screen. */
  cx: number
  cy: number
  /** Half of its layout width and height. */
  halfW: number
  halfH: number
}

export interface Tilt {
  /** CSS rotateX and rotateY in degrees. */
  rx: number
  ry: number
  /** Pointer position inside the card as fractions 0 to 1 (for the glow). */
  gx: number
  gy: number
}

const clamp = (value: number, low: number, high: number) => Math.min(high, Math.max(low, value))

/** 6 degrees for a card up to 320 px, fading linearly to 1.5 degrees for a document of 1120 px. */
export function maxTiltDegrees(width: number, height: number): number {
  const size = Math.max(width, height)
  return clamp(6 - ((size - 320) * 4.5) / 800, 1.5, 6)
}

export function tiltAngles(x: number, y: number, box: TiltBox, maxDeg: number): Tilt {
  const nx = box.halfW > 0 ? clamp((x - box.cx) / box.halfW, -1, 1) : 0
  const ny = box.halfH > 0 ? clamp((y - box.cy) / box.halfH, -1, 1) : 0
  return {
    // `+ 0` turns a negative zero into a plain zero.
    ry: nx * maxDeg + 0,
    rx: 0 - ny * maxDeg + 0,
    gx: (nx + 1) / 2,
    gy: (ny + 1) / 2,
  }
}

export function tiltCapable(env: {
  finePointer: boolean
  reducedMotion: boolean
  lowBandwidth: boolean
}): boolean {
  return env.finePointer && !env.reducedMotion && !env.lowBandwidth
}
