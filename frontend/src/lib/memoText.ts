import type { AnalysisResult } from '../api/analyses'

/**
 * The memo as plain text, for copying and sharing. It is built from the result the page already
 * holds; nothing is fetched or stored.
 */
export function memoText(result: AnalysisResult): string {
  const { memo } = result
  const findings = new Map(result.findings.map((finding) => [finding.id, finding]))
  const redlines = new Map(result.redlines.map((redline) => [redline.id, redline]))
  const citations = new Map(
    result.findings
      .flatMap((finding) => finding.citations)
      .concat(result.redlines.flatMap((redline) => redline.citations))
      .map((citation) => [citation.citationId, citation]),
  )
  const obligations = new Map(result.obligations.map((item) => [item.id, item]))
  const lines: string[] = [memo.title, '']
  for (const section of memo.sections) {
    lines.push(section.heading)
    for (const block of section.blocks) {
      switch (block.type) {
        case 'paragraph':
          lines.push(block.text)
          break
        case 'list':
          block.items.forEach((item) => lines.push(`- ${item}`))
          break
        case 'riskTable':
          block.findingIds.forEach((id) => {
            const finding = findings.get(id)
            if (finding !== undefined) {
              lines.push(`- ${finding.title} (${finding.severity})`)
            }
          })
          break
        case 'findingRef': {
          const finding = findings.get(block.findingId)
          if (finding !== undefined) {
            lines.push(finding.title)
          }
          break
        }
        case 'citationChip': {
          const citation = citations.get(block.citationId)
          if (citation !== undefined) {
            lines.push(`  [${citation.actCode} ${citation.sectionNo}]`)
          }
          break
        }
        case 'redlineRef': {
          const redline = redlines.get(block.redlineId)
          if (redline?.proposedText != null) {
            lines.push(redline.proposedText)
          }
          break
        }
        case 'obligationTable':
          block.obligationIds.forEach((id) => {
            const item = obligations.get(id)
            if (item !== undefined) {
              lines.push(`- ${item.obligation}`)
            }
          })
          break
      }
    }
    lines.push('')
  }
  lines.push(result.disclaimer)
  return lines.join('\n')
}

/** The part of the memo worth reading aloud: its summary and next steps, not the tables. */
export function memoSpeech(result: AnalysisResult): string {
  return result.memo.sections
    .filter((section) => section.kind === 'SUMMARY' || section.kind === 'NEXT_STEPS')
    .flatMap((section) => [
      section.heading,
      ...section.blocks.flatMap((block) =>
        block.type === 'paragraph' ? [block.text] : block.type === 'list' ? block.items : [],
      ),
    ])
    .join('. ')
}
