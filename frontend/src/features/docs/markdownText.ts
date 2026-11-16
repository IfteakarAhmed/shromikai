/** Text helpers for the docs pages: heading ids and the cutting of one section out of a document. */

/** An id for a heading: lower case, spaces to hyphens, letters of any script kept. */
export function slug(text: string): string {
  return text
    .toLowerCase()
    .replace(/`/g, '')
    .replace(/[^\p{L}\p{M}\p{N}\s-]/gu, '')
    .trim()
    .replace(/\s+/g, '-')
}

/** The lines of a section: from its heading to the next heading of the same or a higher level. */
export function sectionOf(source: string, heading: string): string {
  const lines = source.split('\n')
  const level = /^#+/.exec(heading)?.[0].length ?? 0
  const start = lines.findIndex((line) => line.trim() === heading)
  if (start < 0 || level === 0) {
    return ''
  }
  let end = lines.length
  let inFence = false
  for (let i = start + 1; i < lines.length; i += 1) {
    const line = lines[i] ?? ''
    if (line.startsWith('```')) {
      inFence = !inFence
    }
    const match = inFence ? null : /^(#+)\s/.exec(line)
    if (match !== null && (match[1]?.length ?? 99) <= level) {
      end = i
      break
    }
  }
  return lines.slice(start, end).join('\n').trimEnd()
}
