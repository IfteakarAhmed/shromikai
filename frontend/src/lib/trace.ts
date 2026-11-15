import type { AnalysisTrace } from '../api/analyses'

/** One check of a claimed section for a finding, from the VERIFY stage of the trace. */
export interface Verdict {
  sectionKey: string
  status: 'VERIFIED' | 'UNCERTAIN' | 'REJECTED'
  attempt: number
  reasons: string[]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/** How many sections retrieval brought to the analysis (the RETRIEVE stage), when it says. */
export function retrievedCount(trace: AnalysisTrace): number | null {
  const detail = trace.events.find((event) => event.stage === 'RETRIEVE')?.detail
  const count = isRecord(detail) ? detail['candidateSections'] : undefined
  return typeof count === 'number' ? count : null
}

/** What the verifier said about each section claimed for a finding, first attempt first. */
export function verdictsFor(trace: AnalysisTrace, findingId: string): Verdict[] {
  const detail = trace.events.find((event) => event.stage === 'VERIFY')?.detail
  const verdicts = isRecord(detail) ? detail['verdicts'] : undefined
  if (!Array.isArray(verdicts)) {
    return []
  }
  const out: Verdict[] = []
  for (const item of verdicts as unknown[]) {
    if (
      isRecord(item) &&
      item['finding'] === findingId &&
      typeof item['sectionKey'] === 'string' &&
      (item['status'] === 'VERIFIED' ||
        item['status'] === 'UNCERTAIN' ||
        item['status'] === 'REJECTED')
    ) {
      out.push({
        sectionKey: item['sectionKey'],
        status: item['status'],
        attempt: typeof item['attempt'] === 'number' ? item['attempt'] : 1,
        reasons: Array.isArray(item['reasons'])
          ? (item['reasons'] as unknown[]).filter((r): r is string => typeof r === 'string')
          : [],
      })
    }
  }
  return out
}
