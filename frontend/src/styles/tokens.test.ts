import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { cssColorToHex } from '../features/landing/oklch'

/**
 * The design tokens of both themes (C1 visual language). Every text colour the app uses must reach
 * WCAG AA (4.5:1) on the surfaces it sits on, in dark (the default) and in light.
 */
const css = readFileSync(resolve(__dirname, '../index.css'), 'utf8')

function block(selector: string): Record<string, string> {
  const start = css.indexOf(selector)
  if (start < 0) {
    throw new Error(`no ${selector} block`)
  }
  const body = css.slice(css.indexOf('{', start) + 1, css.indexOf('\n}', start))
  const tokens: Record<string, string> = {}
  for (const match of body.matchAll(/--([\w-]+):\s*([^;]+);/g)) {
    tokens[match[1] ?? ''] = (match[2] ?? '').trim()
  }
  return tokens
}

const dark = block(":root,\n[data-surface='ink'] {")
const light = { ...dark, ...block(":root[data-theme='light'] {") }

function resolveToken(tokens: Record<string, string>, name: string): string {
  let value = tokens[name]
  for (let i = 0; i < 4 && value?.startsWith('var(--'); i += 1) {
    value = tokens[value.slice(6, -1)]
  }
  if (value === undefined) {
    throw new Error(`missing token --${name}`)
  }
  return value
}

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
  const [r, g, b] = channels.map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0)
}

function contrast(tokens: Record<string, string>, fg: string, bg: string): number {
  const a = luminance(cssColorToHex(resolveToken(tokens, fg)))
  const b = luminance(cssColorToHex(resolveToken(tokens, bg)))
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}

const TEXT_ON_SURFACES: readonly [string, string][] = [
  ['foreground', 'background'],
  ['foreground', 'surface-1'],
  ['foreground', 'surface-2'],
  ['foreground', 'surface-3'],
  ['muted-foreground', 'background'],
  ['muted-foreground', 'surface-1'],
  ['muted-foreground', 'surface-2'],
  ['primary', 'background'],
  ['primary', 'surface-1'],
  ['primary-foreground', 'primary'],
  ['verified', 'surface-1'],
  ['uncertain', 'surface-1'],
  ['rejected', 'surface-1'],
  ['sev-critical', 'surface-1'],
  ['sev-high', 'surface-1'],
  ['sev-medium', 'surface-1'],
  ['sev-low', 'surface-1'],
]

describe('design tokens', () => {
  it('NFR-08 define the layered surfaces, glass, glow, type and motion tokens', () => {
    for (const name of [
      'surface-1',
      'surface-2',
      'surface-3',
      'glass',
      'glass-border',
      'glow-primary',
      'glow-uncertain',
      'orb-1',
      'orb-2',
      'orb-3',
      'font-display',
      'font-body',
      'font-ui',
      'dur-fast',
      'dur-base',
      'dur-slow',
      'ease-out',
    ]) {
      expect(dark[name], `--${name}`).toBeDefined()
    }
  })

  it('NFR-08 keep a deep ink-navy ground in the default dark theme', () => {
    const hex = cssColorToHex(resolveToken(dark, 'background'))
    expect(luminance(hex)).toBeLessThan(0.012)
    const [r, , b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
    expect(b ?? 0).toBeGreaterThan(r ?? 0)
  })

  for (const [theme, tokens] of [
    ['dark', dark],
    ['light', light],
  ] as const) {
    it(`NFR-08 every text colour reaches 4.5:1 on its surfaces (${theme})`, () => {
      const failures = TEXT_ON_SURFACES.map(
        ([fg, bg]) => [fg, bg, contrast(tokens, fg, bg)] as const,
      ).filter(([, , ratio]) => ratio < 4.5)
      expect(failures.map(([fg, bg, ratio]) => `${fg} on ${bg}: ${ratio.toFixed(2)}`)).toEqual([])
    })
  }
})
