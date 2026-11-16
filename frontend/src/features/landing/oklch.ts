/**
 * A CSS colour from a design token as a hex string, for the 3D scene. The tokens are `oklch(L C h)`;
 * they are converted by arithmetic, not by drawing on a canvas and reading the pixel back, which
 * made the browser stall the GPU and log warnings. Anything unreadable becomes white.
 */
export function cssColorToHex(value: string): string {
  const trimmed = value.trim()
  if (/^#[0-9a-f]{6}$/i.test(trimmed)) {
    return trimmed.toLowerCase()
  }
  const match = /^oklch\(\s*([\d.]+)(%?)\s+([\d.]+)\s+([\d.]+)(?:deg)?\s*(?:\/[^)]*)?\)$/i.exec(
    trimmed,
  )
  if (match === null) {
    return '#ffffff'
  }
  const lightness = Number(match[1]) / (match[2] === '%' ? 100 : 1)
  const chroma = Number(match[3])
  const hue = (Number(match[4]) * Math.PI) / 180
  const a = chroma * Math.cos(hue)
  const b = chroma * Math.sin(hue)
  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const s = (lightness - 0.0894841775 * a - 1.291485548 * b) ** 3
  const linear = [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ]
  return `#${linear
    .map((channel) => {
      const clamped = Math.min(1, Math.max(0, channel))
      const encoded = clamped <= 0.0031308 ? 12.92 * clamped : 1.055 * clamped ** (1 / 2.4) - 0.055
      return Math.round(encoded * 255)
        .toString(16)
        .padStart(2, '0')
    })
    .join('')}`
}
