import { createElement, type ReactNode } from 'react'

/**
 * One look for every chart (C4). Everything is a theme token (`var(--…)`), so a chart follows the
 * dark and the light theme without being redrawn, and every series reaches 3:1 on the card
 * surfaces in both (`tokens.test.ts`). A chart is a picture of the table beside it.
 */

/** The usage series, in the order of the table's columns. Coral and amber are not neighbours. */
export const usageSeries = [
  { key: 'analyses', color: 'var(--verified)' },
  { key: 'compares', color: 'var(--sev-medium)' },
  { key: 'memoRenders', color: 'var(--uncertain)' },
  { key: 'apiCalls', color: 'var(--sev-critical)' },
  { key: 'mcpCalls', color: 'var(--sev-low)' },
] as const

export type UsageSeriesKey = (typeof usageSeries)[number]['key']

export function seriesColor(key: UsageSeriesKey): string {
  const found = usageSeries.find((series) => series.key === key)
  if (found === undefined) {
    throw new Error(`unknown series ${key}`)
  }
  return found.color
}

/** A single-series chart (cost, found questions) uses the brand green. */
export const singleSeries = 'var(--primary)'

export const axisTick = {
  fill: 'var(--muted-foreground)',
  fontSize: 12,
  fontFamily: 'var(--font-ui)',
} as const

export const chartGrid = { stroke: 'var(--border)', strokeDasharray: '3 3' } as const

/** A thin edge in the colour of the card, so stacked marks are told apart without a gap. */
export const markEdge = 'var(--surface-1)'

export const tooltipProps = {
  contentStyle: {
    background: 'var(--surface-2)',
    border: '1px solid var(--glass-border)',
    borderRadius: 12,
    boxShadow: 'var(--shadow-glass)',
    color: 'var(--foreground)',
  },
  itemStyle: { color: 'var(--foreground)' },
  labelStyle: { color: 'var(--muted-foreground)' },
  cursor: { fill: 'var(--chart-cursor)' },
} as const

/** Legend text in the foreground colour (Recharts would paint it in the series colour). */
export function legendFormatter(value: string): ReactNode {
  return createElement('span', { style: { color: 'var(--foreground)' } }, value)
}
