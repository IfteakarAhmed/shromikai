import type { AnalysisResult } from '../api/analyses'

/**
 * Plain-language pieces of the result screen (C3), computed only from the API response: the
 * number of big problems, whether verification has really finished (the seal), the parts of this
 * result a reader can open next, and the first sentence of a finding for the compact card.
 */
export type NextStep =
  | { id: 'proposals'; count: number }
  | { id: 'review'; count: number; ticketId: string }
  | { id: 'duties'; count: number }
  | { id: 'memo' }

const FINAL_STATUSES: readonly string[] = ['VERIFIED', 'UNCERTAIN', 'POLICY_ONLY']

/** "Big" problems are the CRITICAL and HIGH ones, counted by the API. */
export function bigProblemCount(summary: AnalysisResult['summary']): number {
  return summary.findingsBySeverity.CRITICAL + summary.findingsBySeverity.HIGH
}

/**
 * The seal may say "verification finished" only when the analysis succeeded, a verifier really
 * ran, there was something to verify and every finding has a final verification status.
 */
export function verificationFinished(result: AnalysisResult): boolean {
  return (
    // The type says SUCCEEDED, but a malformed or future response must never earn the seal.
    (result.status as string) === 'SUCCEEDED' &&
    result.provenance.verifierIndependence !== 'NONE' &&
    result.findings.length > 0 &&
    result.findings.every((finding) => FINAL_STATUSES.includes(finding.verificationStatus))
  )
}

/** The parts of this result to open next, only those that exist, in a fixed order. */
export function nextSteps(result: AnalysisResult): NextStep[] {
  const steps: NextStep[] = []
  if (result.redlines.length > 0) {
    steps.push({ id: 'proposals', count: result.redlines.length })
  }
  if (result.summary.uncertainFindings > 0 && result.reviewTicket !== null) {
    steps.push({
      id: 'review',
      count: result.summary.uncertainFindings,
      ticketId: result.reviewTicket.ticketId,
    })
  }
  if (result.obligations.length > 0) {
    steps.push({ id: 'duties', count: result.obligations.length })
  }
  steps.push({ id: 'memo' })
  return steps
}

/**
 * The first sentence and the rest. A sentence ends at a danda, a full stop, a question or an
 * exclamation mark followed by a space; a full stop inside a number (2.5) never ends one.
 */
export function firstSentence(text: string): { first: string; rest: string } {
  const trimmed = text.trim()
  const match = /^(.+?[।.!?])\s+(\S[\s\S]*)$/u.exec(trimmed)
  if (match === null) {
    return { first: trimmed, rest: '' }
  }
  return { first: match[1] ?? trimmed, rest: (match[2] ?? '').trim() }
}
