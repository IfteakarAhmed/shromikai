import type { AnalysisResult } from '../api/analyses'

export type Block = AnalysisResult['memo']['sections'][number]['blocks'][number]

/** A finding that cites two passages of one section lists the section once in the memo. */
export function withoutRepeatedSections(
  blocks: readonly Block[],
  citations: ReadonlyMap<string, string>,
): Block[] {
  const shown = new Set<string>()
  return blocks.filter((block) => {
    if (block.type === 'findingRef') {
      shown.clear()
      return true
    }
    if (block.type !== 'citationChip') {
      return true
    }
    const key = citations.get(block.citationId) ?? block.citationId
    if (shown.has(key)) {
      return false
    }
    shown.add(key)
    return true
  })
}
