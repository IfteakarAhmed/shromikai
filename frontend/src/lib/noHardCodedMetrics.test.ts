import { describe, expect, it } from 'vitest'

/**
 * FR-74, R-07: no metric is written into the UI. A percentage in source or in a translation would be a
 * number nobody can trace to a report, so only layout widths of 100% are allowed.
 */
const sources = import.meta.glob<string>(['/src/**/*.{ts,tsx}', '/src/i18n/*.json'], {
  query: '?raw',
  import: 'default',
  eager: true,
})

const PERCENT = /(?<![\w.#-])[0-9০-৯]+(?:[.,][0-9০-৯]+)?\s?%/g

describe('no hard-coded metrics (FR-74)', () => {
  it('FR-74 finds no percentage literal in the UI sources or translations', () => {
    const found: string[] = []
    for (const [file, text] of Object.entries(sources)) {
      if (/\.test\.|\/test\/|types\.ts|openapi\.json/.test(file)) {
        continue
      }
      for (const match of text.matchAll(PERCENT)) {
        const literal = match[0]
        const line = text.slice(0, match.index).split('\n').length
        const lineText = text.split('\n')[line - 1] ?? ''
        const isComment = /^\s*(\/\/|\/\*|\*)/.test(lineText)
        if (literal.trim() !== '100%' && !isComment) {
          found.push(`${file}:${String(line)} ${literal}`)
        }
      }
    }
    expect(found).toEqual([])
  })
})
