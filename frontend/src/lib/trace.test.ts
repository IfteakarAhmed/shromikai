import { describe, expect, it } from 'vitest'
import type { AnalysisTrace } from '../api/analyses'
import { retrievedCount, verdictsFor } from './trace'

function trace(events: { stage: string; detail: Record<string, unknown> }[]): AnalysisTrace {
  return {
    analysisId: 'a',
    createdAt: '2026-11-16T10:00:00Z',
    totals: {
      tokensIn: 0,
      tokensOut: 0,
      cacheReadTokens: 0,
      cacheWriteTokens: 0,
      costUsdMicros: 0,
    },
    events: events.map((e) => ({
      ...e,
      at: '2026-11-16T10:00:00Z',
      durationMs: 1,
      promptId: null,
      promptVersion: null,
      modelId: null,
      tokensIn: null,
      tokensOut: null,
      cacheReadTokens: null,
      cacheWriteTokens: null,
      costUsdMicros: null,
      outcome: 'OK',
    })),
  } as AnalysisTrace
}

describe('trace readers', () => {
  it('FR-70 reads how many sections retrieval brought, or null when it is not in the trace', () => {
    expect(retrievedCount(trace([{ stage: 'RETRIEVE', detail: { candidateSections: 40 } }]))).toBe(
      40,
    )
    expect(retrievedCount(trace([{ stage: 'RISK', detail: {} }]))).toBeNull()
  })

  it('FR-70 reads the verdicts of one finding, in order, with their reason codes', () => {
    const t = trace([
      {
        stage: 'VERIFY',
        detail: {
          verdicts: [
            { finding: 'f1', sectionKey: 'XX:5@o', status: 'VERIFIED', attempt: 1, reasons: [] },
            {
              finding: 'f2',
              sectionKey: 'XX:6@o',
              status: 'UNCERTAIN',
              attempt: 1,
              reasons: ['SUPPORT_PARTIAL'],
            },
            {
              finding: 'f2',
              sectionKey: 'XX:7@o',
              status: 'REJECTED',
              attempt: 2,
              reasons: ['QUOTE_NOT_FOUND'],
            },
          ],
        },
      },
    ])

    expect(verdictsFor(t, 'f2')).toEqual([
      { sectionKey: 'XX:6@o', status: 'UNCERTAIN', attempt: 1, reasons: ['SUPPORT_PARTIAL'] },
      { sectionKey: 'XX:7@o', status: 'REJECTED', attempt: 2, reasons: ['QUOTE_NOT_FOUND'] },
    ])
    expect(verdictsFor(t, 'f9')).toEqual([])
  })

  it('FR-70 ignores verdicts that are malformed instead of failing', () => {
    const t = trace([
      {
        stage: 'VERIFY',
        detail: {
          verdicts: [{ finding: 'f1' }, 'x', { finding: 'f1', sectionKey: 'a', status: 'NOPE' }],
        },
      },
    ])

    expect(verdictsFor(t, 'f1')).toEqual([])
  })
})
