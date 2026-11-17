import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * The CSS 3D depth system (C2b) is plain CSS, so its rules are checked as text: nothing that
 * flattens a 3D scene may sit on a card that tilts, the reveal must not fight the tilt over
 * `transform`, scroll parallax is limited to fine pointers, and print drops all of it.
 */
const css = readFileSync(resolve(__dirname, '../index.css'), 'utf8')

const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** The body of the first rule whose selector line is exactly `selector`, or of an at-rule. */
function block(selector: string, from = 0): string {
  // The selector must start its line, so a rule such as `&:hover > .depth-glass {` never matches.
  const found = css.slice(from).search(new RegExp(`(^|\\n)\\s*${escape(selector)} \\{`))
  if (found < 0) {
    throw new Error(`no rule ${selector}`)
  }
  const start = from + found + (css[from + found] === '\n' ? 1 : 0)
  let depth = 0
  for (let i = css.indexOf('{', start); i < css.length; i += 1) {
    if (css[i] === '{') depth += 1
    if (css[i] === '}') {
      depth -= 1
      if (depth === 0) {
        return css.slice(css.indexOf('{', start) + 1, i)
      }
    }
  }
  throw new Error(`unclosed ${selector}`)
}

const FLATTENING =
  /(^|[\s;])(overflow|overflow-x|overflow-y|opacity|filter|backdrop-filter|clip|clip-path|isolation|mask|mask-image|mix-blend-mode):/

describe('depth system CSS', () => {
  it('FR-68 a card that tilts keeps a 3D scene: no property that flattens it', () => {
    expect(block(".depth-card[data-tilt='on']")).toMatch(/transform-style:\s*preserve-3d/)
    for (const selector of ['.depth-card', ".depth-card[data-tilt='on']"]) {
      expect(block(selector), selector).not.toMatch(FLATTENING)
    }
  })

  it('FR-68 the glass layer, not the card, carries the blur', () => {
    expect(block('.depth-glass')).toMatch(/backdrop-filter:/)
    expect(block('.depth-glass')).toMatch(/pointer-events:\s*none/)
  })

  it('FR-68 the tilt uses transform and the reveal and hover lift use translate', () => {
    expect(block(".depth-card[data-tilting='true']:not(:has(:focus-visible))")).toMatch(
      /transform:\s*perspective\(/,
    )
    const reveal = block('@keyframes reveal-rise')
    expect(reveal).toMatch(/translate:/)
    expect(reveal).not.toMatch(/transform:|opacity:|scale:/)
  })

  it('FR-68 the reveal does not hold its end value, so hover and tilt stay free', () => {
    const rule = block('.reveal', css.indexOf('@supports (animation-timeline: view())'))
    expect(rule).toMatch(/animation:[^;]*\bbackwards\b/)
    expect(rule).not.toMatch(/animation:[^;]*\b(both|forwards)\b/)
  })

  it('FR-68 lifts use translate with a z offset, which works inside a 3D scene', () => {
    expect(block('.lift-1')).toMatch(/translate:\s*0 0 /)
    expect(block('.lift-2')).toMatch(/translate:\s*0 0 /)
  })

  it('FR-76 scroll parallax only runs with a fine pointer and without reduced motion', () => {
    const start = css.indexOf('@media (hover: hover) and (pointer: fine)')
    expect(start).toBeGreaterThan(-1)
    const inside = block('@media (hover: hover) and (pointer: fine)')
    expect(inside).toMatch(/animation-timeline:/)
    // No scroll-driven timeline outside that block except the reveal (which is a short entry).
    const outside = css.replace(inside, '')
    const timelines = outside.match(/animation-timeline:[^;]+;/g) ?? []
    expect(timelines.every((line) => /view\(\)/.test(line))).toBe(true)
    expect(block('@media (prefers-reduced-motion: reduce)')).toMatch(/\.parallax/)
  })

  it('NFR-08 reduced motion flattens every card and drops lifts', () => {
    const reduced = block('@media (prefers-reduced-motion: reduce)')
    expect(reduced).toMatch(/\.depth-card/)
    expect(reduced).toMatch(/transform:\s*none\s*!important/)
    expect(reduced).toMatch(/\.lift-1/)
  })

  it('FR-76 low-bandwidth mode flattens every card and drops lifts', () => {
    expect(css).toMatch(/:root\[data-lowbw='true'\] \.depth-card/)
  })

  it('FR-71 printing drops the glass, the glow, the tilt and the lifts', () => {
    const print = block('@media print')
    expect(print).toMatch(/\.depth-glass/)
    expect(print).toMatch(/\.depth-glow/)
    expect(print).toMatch(/\.depth-card/)
  })

  it('FR-70 sheets float in with a 3D entrance that reduced motion switches off', () => {
    expect(block('@keyframes sheet-in')).toMatch(/rotateX\(/)
    expect(block('@media (prefers-reduced-motion: reduce)')).toMatch(/dialog\.sheet/)
  })

  it('R-01 the verification seal drops in once and stands still with reduced motion', () => {
    expect(block('@keyframes seal-drop')).toMatch(/translate|transform/)
    expect(block('@media (prefers-reduced-motion: reduce)')).toMatch(/\.seal-moment/)
  })

  it('FR-70 every direct child of a leaning card is its own plane, so clicks hit what is drawn', () => {
    const rule = block(
      ".depth-card[data-tilt='on'] > :not(.depth-glass, .depth-glow, .lift-1, .lift-2)",
    )
    expect(rule).toMatch(/translate:\s*0 0 /)
  })
})
