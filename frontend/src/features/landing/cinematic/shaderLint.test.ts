import { describe, expect, it } from 'vitest'
import * as shaders from './shaders'

/**
 * A lint for the GLSL of the cinematic scene. On some GPUs (ANGLE on Metal, for one) pow() of a
 * negative base, sqrt() of a negative value, a reversed smoothstep() or normalize() of a zero
 * vector gives NaN; additive blending and the bloom blur then spread it into large black shapes.
 * Every such call must carry its own guard.
 */
const sources: [string, string][] = Object.entries(shaders as Record<string, unknown>).flatMap(
  ([name, value]) => (typeof value === 'string' ? [[name, value] as [string, string]] : []),
)

function code(source: string): string {
  return source.replace(/\/\/[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '')
}

const GUARDS = /^(max|clamp|abs|saturate)\(/

function violations(name: string, source: string): string[] {
  const text = code(source)
  const found: string[] = []
  for (const match of text.matchAll(/\bpow\(\s*/g)) {
    const rest = text.slice(match.index + match[0].length)
    if (!GUARDS.test(rest)) {
      found.push(`${name}: unguarded pow(${rest.slice(0, 24)}`)
    }
  }
  for (const match of text.matchAll(/\bsqrt\(\s*/g)) {
    const rest = text.slice(match.index + match[0].length)
    if (!GUARDS.test(rest)) {
      found.push(`${name}: unguarded sqrt(${rest.slice(0, 24)}`)
    }
  }
  for (const match of text.matchAll(/\bsmoothstep\(\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*,/g)) {
    if (Number(match[1]) >= Number(match[2])) {
      found.push(`${name}: reversed ${match[0]}`)
    }
  }
  if (/\bnormalize\(/.test(text)) {
    found.push(`${name}: normalize() without the zero-length guard (use safeNormalize)`)
  }
  for (const match of text.matchAll(/\/\s*(-?\s*[A-Za-z_(][^;,)]*)/g)) {
    const denominator = (match[1] ?? '').trim()
    if (!/^(max|clamp)\(/.test(denominator) && !/^[\d.]+$/.test(denominator)) {
      found.push(`${name}: division by ${denominator.slice(0, 24)}`)
    }
  }
  return found
}

describe('cinematic shaders', () => {
  it('FR-68 there are shaders to check', () => {
    expect(sources.length).toBeGreaterThan(10)
  })

  it('FR-68 guard pow, sqrt, smoothstep, normalize and division against NaN on real GPUs', () => {
    expect(sources.flatMap(([name, source]) => violations(name, source))).toEqual([])
  })

  it('FR-68 the bloom passes clamp their colour to a finite range', () => {
    for (const name of ['brightFragment', 'blurFragment', 'compositeFragment'] as const) {
      expect(code(shaders[name]), name).toMatch(/clamp\(/)
    }
  })
})
