/**
 * The colours and light of the cinematic scene for each theme (C2a): dark is a night sky, light a
 * soft dawn. A pure function of the theme tokens, so both looks are tested without a GPU. On a
 * light sky additive glow would wash out, so the dawn uses normal blending and a gentler bloom.
 */
export type Scheme = 'dark' | 'light'

/** Theme tokens as hex colours (read from the CSS custom properties). */
export interface ThemeTokens {
  foreground: string
  background: string
  verified: string
  uncertain: string
  rejected: string
}

export interface ScenePalette {
  scheme: Scheme
  /** Sky gradient behind everything. */
  skyTop: string
  skyBottom: string
  /** Body of the glass sheets and law cards. */
  glass: string
  /** Ruled lines and card text shapes. */
  stroke: string
  /** Redaction bars and the check mark on the seal. */
  bar: string
  /** Fresnel rim and sheet borders. */
  rim: string
  verified: string
  uncertain: string
  rejected: string
  /** Light shafts. */
  shaft: string
  dust: string
  glowBlending: 'additive' | 'normal'
  bloomThreshold: number
  bloomStrength: number
  /** Brightness kept in the corners by the vignette (1 = no vignette). */
  vignetteFloor: number
  /** Shaft opacity scale. */
  shaftIntensity: number
  /** Accent used for glows, pulses and the shield (luminous enough to read on the sky). */
  glow: string
  /** Opacity multiplier for glass sheets and law cards. */
  glassOpacity: number
  /** Warm or cool light near the top of the sky. */
  sun: string
  /** The seal disc and the colour of its check mark (readable on it, AA). */
  sealFill: string
  sealMark: string
  /** Law lattice: line colour, opacity scale and how fast it fades with distance (0 = never). */
  latticeLine: string
  latticeOpacity: number
  latticeDepthFade: number
  /** The soft ring that spreads from the seal: colour and peak opacity. */
  shockwave: string
  shockwaveAlpha: number
}

function channels(hex: string): [number, number, number] {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)) as [number, number, number]
}

/** Mixes two hex colours: `amount` 0 gives `a`, 1 gives `b`. */
export function mixHex(a: string, b: string, amount: number): string {
  const x = channels(a)
  const y = channels(b)
  return `#${x
    .map((value, i) =>
      Math.round(value + ((y[i] ?? value) - value) * amount)
        .toString(16)
        .padStart(2, '0'),
    )
    .join('')}`
}

function relativeLuminance(hex: string): number {
  const [r, g, b] = channels(hex).map((value) => {
    const c = value / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0)
}

/** WCAG contrast ratio of two hex colours. */
export function contrastRatio(a: string, b: string): number {
  const [hi, lo] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x)
  return ((hi ?? 0) + 0.05) / ((lo ?? 0) + 0.05)
}

/** Of near-white and deep navy, the one that reads better on `fill` (always AA for a green). */
function markOn(fill: string): string {
  const light = '#ffffff'
  const dark = '#0b1020'
  return contrastRatio(light, fill) >= contrastRatio(dark, fill) ? light : dark
}

export function scenePalette(scheme: Scheme, tokens: ThemeTokens): ScenePalette {
  const shared = {
    scheme,
    verified: tokens.verified,
    uncertain: tokens.uncertain,
    rejected: tokens.rejected,
  }
  if (scheme === 'dark') {
    return {
      ...shared,
      skyTop: mixHex(tokens.background, '#23306b', 0.35),
      skyBottom: tokens.background,
      glass: mixHex(tokens.foreground, '#000000', 0.74),
      stroke: mixHex(tokens.foreground, '#000000', 0.2),
      bar: tokens.background,
      rim: tokens.foreground,
      shaft: mixHex(tokens.foreground, tokens.verified, 0.25),
      dust: mixHex(tokens.foreground, tokens.verified, 0.15),
      glowBlending: 'additive',
      bloomThreshold: 0.72,
      bloomStrength: 0.75,
      vignetteFloor: 0.72,
      shaftIntensity: 1,
      glow: tokens.verified,
      glassOpacity: 1,
      sun: mixHex(tokens.background, '#3b4a8f', 0.45),
      sealFill: tokens.verified,
      sealMark: markOn(tokens.verified),
      latticeLine: mixHex(tokens.foreground, tokens.background, 0.3),
      latticeOpacity: 1,
      latticeDepthFade: 1,
      shockwave: tokens.verified,
      shockwaveAlpha: 0.85,
    }
  }
  return {
    ...shared,
    // Dawn: cool lavender above, warm peach near the horizon, a low sun to the upper right.
    skyTop: '#dfe3f6',
    skyBottom: '#fbe6d4',
    glass: '#f7f9ff',
    stroke: mixHex(tokens.foreground, '#ffffff', 0.12),
    bar: tokens.foreground,
    rim: mixHex(tokens.foreground, '#9fb4ff', 0.35),
    shaft: '#fff0d8',
    dust: '#c08a52',
    glowBlending: 'normal',
    bloomThreshold: 0.9,
    bloomStrength: 0.3,
    vignetteFloor: 0.94,
    shaftIntensity: 3.4,
    glow: mixHex(tokens.verified, '#10b981', 0.55),
    glassOpacity: 4,
    sun: '#ffd6a8',
    // A deeper emerald than the token so the white check mark reads at 4.5:1 or better.
    sealFill: tokens.verified,
    sealMark: markOn(tokens.verified),
    // Navy lines and quietly lit cards that stay visible even when the camera is far away.
    latticeLine: mixHex(tokens.foreground, '#ffffff', 0.05),
    latticeOpacity: 3.6,
    latticeDepthFade: 0.25,
    // A saturated emerald light, soft-edged in the shader; never a grey disc.
    shockwave: '#12a874',
    shockwaveAlpha: 0.62,
  }
}
