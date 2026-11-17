import { describe, expect, it } from 'vitest'
import {
  axisTick,
  chartGrid,
  legendFormatter,
  seriesColor,
  tooltipProps,
  usageSeries,
} from './chartTheme'

/** C4: one themed look for every chart. Colours are tokens, so both themes work without a switch. */
describe('chartTheme', () => {
  it('FR-73 series colours are theme tokens, distinct, and the first is the verified green', () => {
    const colours = usageSeries.map((series) => series.color)
    expect(new Set(colours).size).toBe(colours.length)
    for (const colour of colours) {
      expect(colour).toMatch(/^var\(--[\w-]+\)$/)
    }
    expect(seriesColor('analyses')).toBe('var(--verified)')
  })

  it('FR-73 the series are the ones the table beside the chart has, in the same order', () => {
    expect(usageSeries.map((series) => series.key)).toEqual([
      'analyses',
      'compares',
      'memoRenders',
      'apiCalls',
      'mcpCalls',
    ])
  })

  it('FR-73 no two neighbouring series share a hue family (amber next to orange was hard to tell)', () => {
    const colours = usageSeries.map((series) => series.color)
    expect(colours).not.toContain('var(--sev-high)')
    expect(colours.indexOf('var(--uncertain)')).not.toBe(colours.indexOf('var(--sev-critical)') + 1)
  })

  it('FR-73 axis labels, grid, tooltip and legend use tokens only', () => {
    expect(axisTick.fill).toBe('var(--muted-foreground)')
    expect(axisTick.fontSize).toBeGreaterThanOrEqual(12)
    expect(chartGrid.stroke).toBe('var(--border)')
    expect(tooltipProps.contentStyle.background).toMatch(/var\(--/)
    expect(tooltipProps.contentStyle.color).toBe('var(--foreground)')
    expect(tooltipProps.cursor).toMatchObject({ fill: expect.stringContaining('var(--') as string })
    const rendered = legendFormatter('x') as { props: { style: { color: string } } }
    expect(rendered.props.style.color).toBe('var(--foreground)')
  })

  it('FR-73 nothing in the theme is a fixed colour', () => {
    const all = JSON.stringify([axisTick, chartGrid, tooltipProps, usageSeries])
    expect(all).not.toMatch(/#[0-9a-f]{3,8}\b|rgb\(|hsl\(|oklch\(/i)
  })
})
