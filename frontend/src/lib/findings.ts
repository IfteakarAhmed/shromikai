import type { RiskFinding } from '../api/analyses'

const ORDER: Record<RiskFinding['severity'], number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }

/** Most serious first; findings of one severity keep the order the API gave them. */
export function sortFindings(findings: readonly RiskFinding[]): RiskFinding[] {
  return findings
    .map((finding, index) => ({ finding, index }))
    .sort((a, b) => ORDER[a.finding.severity] - ORDER[b.finding.severity] || a.index - b.index)
    .map(({ finding }) => finding)
}
