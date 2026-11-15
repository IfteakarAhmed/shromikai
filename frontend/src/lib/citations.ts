import type { VerifiedCitation } from '../api/analyses'

/**
 * Citations of one finding that point at the same section version. A finding may cite two passages
 * of one section; they are one chip, and the sheet marks both passages.
 */
export interface CitationGroup {
  sectionKey: string
  primary: VerifiedCitation
  quotes: string[]
  /** True when another group of the same list is the same section in another version. */
  showVersion: boolean
}

export function groupCitations(citations: readonly VerifiedCitation[]): CitationGroup[] {
  const groups = new Map<string, CitationGroup>()
  for (const citation of citations) {
    const existing = groups.get(citation.sectionKey)
    if (existing === undefined) {
      groups.set(citation.sectionKey, {
        sectionKey: citation.sectionKey,
        primary: citation,
        quotes: [citation.quote],
        showVersion: false,
      })
    } else if (!existing.quotes.includes(citation.quote)) {
      existing.quotes.push(citation.quote)
    }
  }
  const list = [...groups.values()]
  for (const group of list) {
    group.showVersion = list.some(
      (other) =>
        other !== group &&
        other.primary.actCode === group.primary.actCode &&
        other.primary.sectionNo === group.primary.sectionNo,
    )
  }
  return list
}
