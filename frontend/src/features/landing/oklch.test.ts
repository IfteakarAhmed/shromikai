import { describe, expect, it } from 'vitest'
import { cssColorToHex } from './oklch'

describe('cssColorToHex', () => {
  it('FR-68 converts the oklch design tokens to hex without drawing on a canvas', () => {
    expect(cssColorToHex('oklch(1 0 0)')).toBe('#ffffff')
    expect(cssColorToHex('oklch(0 0 0)')).toBe('#000000')
    // Mid grey: lightness 0.5990 is about #808080 in sRGB.
    expect(cssColorToHex('oklch(0.5999 0 0)')).toBe('#808080')
  })

  it('FR-68 reads a hue and chroma: the verified emerald is green', () => {
    const hex = cssColorToHex('oklch(0.8226 0.1677 163.38)')
    const [r, g, b] = [1, 3, 5].map((i) => Number.parseInt(hex.slice(i, i + 2), 16))
    expect(g).toBeGreaterThan((r ?? 0) + 60)
    expect(g).toBeGreaterThan(b ?? 0)
  })

  it('FR-68 ignores an alpha part and falls back to white for anything it cannot read', () => {
    expect(cssColorToHex('oklch(1 0 0 / 50%)')).toBe('#ffffff')
    expect(cssColorToHex('rebeccapurple')).toBe('#ffffff')
    expect(cssColorToHex('')).toBe('#ffffff')
  })

  it('FR-68 passes hex colours through', () => {
    expect(cssColorToHex('#1a2b3c')).toBe('#1a2b3c')
  })
})
