import { describe, expect, it } from 'vitest'
import { locateQuote, locateQuotes, splitAround } from './highlight'

const TEXT =
  'No worker shall be dismissed.\nThe employer shall give the worker written notice of fourteen days   before dismissal. Other rules follow.'

describe('locateQuote', () => {
  it('FR-70 finds the quoted words exactly where they are in the section text', () => {
    const quote = 'written notice of fourteen days'
    const found = locateQuote(TEXT, quote)

    expect(found).not.toBeNull()
    expect(found?.text.slice(found.span.start, found.span.end)).toBe(quote)
  })

  it('FR-70 finds a quote that differs from the text only in white space', () => {
    const quote = 'fourteen days before dismissal'
    const found = locateQuote(TEXT, quote)

    expect(found).not.toBeNull()
    expect(found?.text.slice(found.span.start, found.span.end)).toBe(
      'fourteen days   before dismissal',
    )
  })

  it('FR-70 finds Bangla text and ignores Unicode composition differences', () => {
    const text = 'শ্রমিককে কোনো কারণ ছাড়া ছাঁটাই করা যাবে না। নোটিশ দিতে হবে।'
    const decomposed = 'ছাঁটাই'.normalize('NFD')

    expect(locateQuote(text, 'কোনো কারণ ছাড়া')).not.toBeNull()
    expect(locateQuote(text.normalize('NFD'), 'কোনো কারণ ছাড়া'.normalize('NFC'))).not.toBeNull()
    expect(decomposed.length).toBeGreaterThan(0)
  })

  it('FR-70 R-01 returns nothing rather than a wrong place when the quote is not in the text', () => {
    expect(locateQuote(TEXT, 'an invented sentence about holidays')).toBeNull()
    expect(locateQuote(TEXT, '')).toBeNull()
  })
})

describe('splitAround', () => {
  it('FR-70 cuts the text into before, quote and after', () => {
    expect(splitAround('abcdef', { start: 2, end: 4 })).toEqual({
      before: 'ab',
      quote: 'cd',
      after: 'ef',
    })
  })
})

describe('locateQuotes', () => {
  it('FR-70 finds several passages, in text order, and counts the ones it cannot place', () => {
    const found = locateQuotes(TEXT, [
      'written notice of fourteen days',
      'No worker shall be dismissed.',
      'a sentence that is not there',
    ])

    expect(found.spans.map((s) => found.text.slice(s.start, s.end))).toEqual([
      'No worker shall be dismissed.',
      'written notice of fourteen days',
    ])
    expect(found.missing).toBe(1)
  })

  it('FR-70 merges passages that overlap into one mark', () => {
    const found = locateQuotes('abcdefghij', ['cdefg', 'efghi'])

    expect(found.spans).toEqual([{ start: 2, end: 9 }])
  })
})
