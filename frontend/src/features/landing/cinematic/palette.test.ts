import { describe, expect, it } from 'vitest'
import { scenePalette, type ThemeTokens } from './palette'

const DARK: ThemeTokens = {
  foreground: '#f4f1ea',
  background: '#0b1020',
  verified: '#3ee0a5',
  uncertain: '#f3b34c',
  rejected: '#f2706b',
}
const LIGHT: ThemeTokens = {
  foreground: '#1c2742',
  background: '#f3f5fa',
  verified: '#0f7a52',
  uncertain: '#8a5a00',
  rejected: '#b23a35',
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0)
}
const contrast = (a: string, b: string) => {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p)
  return ((x ?? 0) + 0.05) / ((y ?? 0) + 0.05)
}

describe('scenePalette', () => {
  it('FR-68 night sky in the dark theme: deep sky, light strokes, additive glow, full bloom', () => {
    const palette = scenePalette('dark', DARK)
    expect(luminance(palette.skyTop)).toBeLessThan(0.02)
    expect(luminance(palette.skyBottom)).toBeLessThan(0.02)
    expect(luminance(palette.stroke)).toBeGreaterThan(luminance(palette.glass))
    expect(palette.glowBlending).toBe('additive')
    expect(palette.bloomStrength).toBeGreaterThanOrEqual(0.7)
    expect(palette.vignetteFloor).toBeLessThan(0.8)
  })

  it('FR-68 soft dawn in the light theme: bright sky, navy strokes, normal blending, gentle bloom', () => {
    const palette = scenePalette('light', LIGHT)
    expect(luminance(palette.skyTop)).toBeGreaterThan(0.6)
    expect(luminance(palette.skyBottom)).toBeGreaterThan(0.6)
    expect(palette.skyTop).not.toBe(palette.skyBottom)
    expect(luminance(palette.stroke)).toBeLessThan(luminance(palette.glass))
    expect(contrast(palette.stroke, palette.glass)).toBeGreaterThan(3)
    expect(palette.glowBlending).toBe('normal')
    expect(palette.bloomThreshold).toBeGreaterThanOrEqual(0.85)
    expect(palette.bloomStrength).toBeLessThanOrEqual(0.4)
    expect(palette.vignetteFloor).toBeGreaterThanOrEqual(0.9)
  })

  it('FR-68 keeps the verification colours from the theme tokens in both schemes', () => {
    for (const [scheme, tokens] of [
      ['dark', DARK],
      ['light', LIGHT],
    ] as const) {
      const palette = scenePalette(scheme, tokens)
      expect(palette.verified).toBe(tokens.verified)
      expect(palette.uncertain).toBe(tokens.uncertain)
      expect(palette.rejected).toBe(tokens.rejected)
      expect(palette.scheme).toBe(scheme)
    }
  })

  it('FR-68 every colour is a six-digit hex value', () => {
    for (const scheme of ['dark', 'light'] as const) {
      const palette = scenePalette(scheme, scheme === 'dark' ? DARK : LIGHT)
      for (const key of ['skyTop', 'skyBottom', 'glass', 'stroke', 'bar', 'rim', 'dust'] as const) {
        expect(palette[key], `${scheme} ${key}`).toMatch(/^#[0-9a-f]{6}$/)
      }
    }
  })

  function saturation(hex: string): number {
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    const max = Math.max(r ?? 0, g ?? 0, b ?? 0)
    const min = Math.min(r ?? 0, g ?? 0, b ?? 0)
    return max === 0 ? 0 : (max - min) / max
  }
  const sky = (palette: ReturnType<typeof scenePalette>) =>
    [palette.skyTop, palette.skyBottom] as const

  it('FR-68 the check mark on the seal reaches AA (4.5:1) against the seal, in both themes', () => {
    for (const [scheme, tokens] of [
      ['dark', DARK],
      ['light', LIGHT],
    ] as const) {
      const palette = scenePalette(scheme, tokens)
      expect(contrast(palette.sealMark, palette.sealFill), `${scheme} seal`).toBeGreaterThanOrEqual(
        4.5,
      )
    }
  })

  it('FR-68 picks a readable mark colour for any seal green, light or dark', () => {
    for (const green of ['#0f7a52', '#0b6b45', '#3ee0a5', '#19b27d', '#2f9e6f', '#a7f3d0']) {
      for (const scheme of ['dark', 'light'] as const) {
        const palette = scenePalette(scheme, {
          ...(scheme === 'dark' ? DARK : LIGHT),
          verified: green,
        })
        expect(
          contrast(palette.sealMark, palette.sealFill),
          `${scheme} ${green}`,
        ).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  it('FR-68 the law lattice stands out from the sky at dawn (lines 3:1, cards brighter than the sky)', () => {
    const palette = scenePalette('light', LIGHT)
    for (const colour of sky(palette)) {
      expect(contrast(palette.latticeLine, colour)).toBeGreaterThanOrEqual(3)
    }
    expect(palette.latticeOpacity).toBeGreaterThanOrEqual(2.5)
    expect(palette.latticeDepthFade).toBeLessThanOrEqual(0.5)
  })

  it('FR-68 the shockwave is a coloured light ring, not a grey one, visible on both skies', () => {
    for (const [scheme, tokens] of [
      ['dark', DARK],
      ['light', LIGHT],
    ] as const) {
      const palette = scenePalette(scheme, tokens)
      expect(saturation(palette.shockwave), `${scheme} saturation`).toBeGreaterThanOrEqual(0.4)
      for (const colour of sky(palette)) {
        expect(contrast(palette.shockwave, colour), `${scheme} on sky`).toBeGreaterThanOrEqual(1.8)
      }
      expect(palette.shockwaveAlpha).toBeGreaterThanOrEqual(0.5)
    }
  })
})
