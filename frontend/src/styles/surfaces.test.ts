import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * C4: tables, forms and charts share one themed look, set in CSS and in three small primitives.
 * The rules are checked as text, and the sources are scanned so a page cannot quietly go back to
 * its own ad-hoc table shell, field classes or chart colours.
 */
const root = resolve(__dirname, '..')
const css = readFileSync(resolve(root, 'index.css'), 'utf8')

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? files(path) : [path]
  })
}

const sources = files(root).filter(
  (path) => /\.tsx?$/.test(path) && !/\.test\.tsx?$/.test(path) && !path.includes('/test/'),
)
const read = (path: string) => readFileSync(path, 'utf8')

const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\:]/g, '\\$&')
function rule(selector: string): string {
  const match = new RegExp(`(^|\\n)\\s*${escape(selector)} \\{([^}]*)\\}`).exec(css)
  if (match === null) {
    throw new Error(`no rule ${selector}`)
  }
  return match[2] ?? ''
}

describe('form fields', () => {
  it('FR-68 one .field class sets the surface, the 3:1 edge, the placeholder, hover, focus and error', () => {
    expect(rule('.field')).toMatch(/background:/)
    expect(rule('.field')).toMatch(/border:[^;]*var\(--input\)/)
    expect(rule('.field')).toMatch(/color:\s*var\(--foreground\)/)
    expect(rule('.field::placeholder')).toMatch(/color:\s*var\(--muted-foreground\)/)
    expect(rule('.field:hover:not(:disabled)')).toMatch(/border-color:/)
    expect(rule('.field:focus-visible')).toMatch(/box-shadow:[^;]*var\(--glow-primary\)/)
    expect(rule(".field[aria-invalid='true']")).toMatch(/border-color:\s*var\(--rejected\)/)
    expect(rule('.field:disabled')).toMatch(/cursor:\s*not-allowed/)
  })

  it('FR-68 no page spells its own field classes any more', () => {
    const offenders = sources.filter((path) => /border-input\b/.test(read(path)))
    expect(offenders.map((path) => path.replace(root, ''))).toEqual([])
  })

  it('FR-68 the field class is a single shared constant', () => {
    const shared = read(resolve(root, 'components/ui/field.ts'))
    expect(shared).toMatch(/export const fieldClass\b/)
    expect(shared).toMatch(/\bfield\b/)
  })
})

describe('tables', () => {
  it('FR-68 .data-table themes the header, the rows, hover and the last row', () => {
    expect(rule('.data-table thead th')).toMatch(/background:/)
    expect(rule('.data-table thead th')).toMatch(/color:\s*var\(--muted-foreground\)/)
    expect(rule('.data-table tbody tr:hover')).toMatch(/background:/)
    expect(rule('.data-table tbody tr:last-child > *')).toMatch(/border-bottom-width:\s*0/)
  })

  it('FR-68 hover and stripes are tints of the theme, never fixed colours', () => {
    const body = [rule('.data-table thead th'), rule('.data-table tbody tr:hover')].join('\n')
    expect(body).not.toMatch(/#[0-9a-f]{3,8}\b|rgb\(|hsl\(/i)
  })

  it('FR-68 no page builds its own glass table shell', () => {
    const offenders = sources.filter((path) => {
      const text = read(path)
      return /glass-panel[^"]*overflow-(x-auto|hidden)/.test(text) || /<table\b/.test(text)
        ? !/TableShell/.test(text) && !path.endsWith('MemoView.tsx')
        : false
    })
    expect(offenders.map((path) => path.replace(root, ''))).toEqual([])
  })
})

describe('glass surfaces', () => {
  it('FR-68 only the site header still uses a plain glass panel', () => {
    const offenders = sources
      .filter((path) => /glass-panel/.test(read(path)))
      .map((path) => path.replace(root, ''))
    expect(offenders).toEqual(['/components/shell/Header.tsx'])
  })
})

describe('charts', () => {
  it('FR-73 every chart takes its colours from the shared chart theme, with no colour of its own', () => {
    const charts = sources.filter((path) => /from 'recharts'/.test(read(path)))
    expect(charts.length).toBeGreaterThanOrEqual(2)
    for (const path of charts) {
      const text = read(path)
      expect(text, path).toMatch(/chartTheme/)
      expect(text, path).not.toMatch(/(fill|stroke)="#|(fill|stroke)="var\(--(?!surface)/)
    }
  })
})
