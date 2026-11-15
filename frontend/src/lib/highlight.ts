/**
 * Where a verified quote sits in the full text of its section (FR-70). The quote was matched during
 * verification, so it is normally a verbatim piece of the text; the match here also tolerates
 * differences in white space and in Unicode composition. When the quote cannot be found the caller
 * shows the text without a highlight: a mark in the wrong place would be worse than none (R-01).
 */

export interface Span {
  start: number
  end: number
}

function collapse(value: string): { text: string; map: number[] } {
  // `map[i]` is the index in the original string of character i of the collapsed one.
  const map: number[] = []
  let text = ''
  let lastWasSpace = true
  for (let i = 0; i < value.length; i += 1) {
    const char = value.charAt(i)
    if (/\s/u.test(char)) {
      if (!lastWasSpace) {
        text += ' '
        map.push(i)
        lastWasSpace = true
      }
    } else {
      text += char
      map.push(i)
      lastWasSpace = false
    }
  }
  return { text, map }
}

/** The text in Unicode NFC (the form the corpus uses) and where the quote sits in it, or null. */
export function locateQuote(original: string, quote: string): { text: string; span: Span } | null {
  const needle = quote.normalize('NFC').trim()
  if (needle === '') {
    return null
  }
  const text = original.normalize('NFC')
  const exact = text.indexOf(needle)
  if (exact >= 0) {
    return { text, span: { start: exact, end: exact + needle.length } }
  }
  const flat = collapse(text)
  const target = collapse(needle).text.trim()
  const at = target === '' ? -1 : flat.text.indexOf(target)
  if (at >= 0) {
    const start = flat.map[at]
    const last = flat.map[at + target.length - 1]
    if (start !== undefined && last !== undefined) {
      return { text, span: { start, end: last + 1 } }
    }
  }
  return null
}

export function splitAround(
  text: string,
  span: Span,
): { before: string; quote: string; after: string } {
  return {
    before: text.slice(0, span.start),
    quote: text.slice(span.start, span.end),
    after: text.slice(span.end),
  }
}
