import { describe, expect, it } from 'vitest'
import bn from '../i18n/bn.json'
import en from '../i18n/en.json'
import { makeFinding, makeResult, makeUncertainFinding } from '../test/fixtures'
import { bigProblemCount, firstSentence, nextSteps, verificationFinished } from './resultSummary'

describe('bigProblemCount', () => {
  it('FR-70 counts CRITICAL and HIGH findings, straight from the API summary', () => {
    const summary = makeResult().summary
    expect(
      bigProblemCount({
        ...summary,
        findingsBySeverity: { CRITICAL: 4, HIGH: 6, MEDIUM: 3, LOW: 2 },
      }),
    ).toBe(10)
    expect(
      bigProblemCount({
        ...summary,
        findingsBySeverity: { CRITICAL: 0, HIGH: 0, MEDIUM: 3, LOW: 0 },
      }),
    ).toBe(0)
  })
})

describe('verificationFinished', () => {
  it('R-01 is true only for a finished analysis whose verifier ran over every finding', () => {
    expect(verificationFinished(makeResult())).toBe(true)
  })

  it('R-01 is false when no verifier ran', () => {
    const result = makeResult()
    expect(
      verificationFinished({
        ...result,
        provenance: { ...result.provenance, verifierIndependence: 'NONE' },
      }),
    ).toBe(false)
  })

  it('R-01 is false when there was nothing to verify', () => {
    expect(verificationFinished(makeResult({ findings: [] }))).toBe(false)
  })

  it('R-01 is false for any status but SUCCEEDED, or a finding without a final status', () => {
    const result = makeResult()
    expect(verificationFinished({ ...result, status: 'RUNNING' as unknown as 'SUCCEEDED' })).toBe(
      false,
    )
    expect(
      verificationFinished(
        makeResult({
          findings: [makeFinding({ verificationStatus: 'PENDING' as unknown as 'VERIFIED' })],
        }),
      ),
    ).toBe(false)
  })
})

describe('nextSteps', () => {
  it('FR-70 lists only the parts this result really has, in a fixed order', () => {
    const result = makeResult({ findings: [makeFinding(), makeUncertainFinding()] })
    expect(nextSteps(result)).toEqual([
      { id: 'proposals', count: result.redlines.length },
      {
        id: 'review',
        count: result.summary.uncertainFindings,
        ticketId: result.reviewTicket?.ticketId,
      },
      { id: 'duties', count: result.obligations.length },
      { id: 'memo' },
    ])
  })

  it('FR-70 leaves out what is not there: no proposals, no review, no duties', () => {
    const result = makeResult({ redlines: [], obligations: [], reviewTicket: null })
    expect(nextSteps({ ...result, summary: { ...result.summary, uncertainFindings: 0 } })).toEqual([
      { id: 'memo' },
    ])
  })

  it('R-01 offers the review only when something is uncertain and a ticket exists', () => {
    const result = makeResult()
    expect(
      nextSteps({ ...result, summary: { ...result.summary, uncertainFindings: 0 } }).map(
        (step) => step.id,
      ),
    ).not.toContain('review')
    expect(nextSteps({ ...result, reviewTicket: null }).map((step) => step.id)).not.toContain(
      'review',
    )
  })

  it('R-12 the next-step words point to parts of this result and never read as legal advice', () => {
    const words = [
      ...Object.values(bn.results.next),
      ...Object.values(en.results.next),
      bn.results.summary.big,
      en.results.summary.big,
    ].join(' ')
    expect(words).not.toMatch(
      /মামলা|আদালত|অভিযোগ করুন|আইনজীবী নিয়োগ|দাবি করুন|sue|court|lawsuit|file a complaint|hire a lawyer|you must|you should/i,
    )
  })
})

describe('firstSentence', () => {
  it('FR-70 splits after the first danda or full stop that ends a sentence', () => {
    expect(firstSentence('প্রথম বাক্য। দ্বিতীয় বাক্য। তৃতীয়।')).toEqual({
      first: 'প্রথম বাক্য।',
      rest: 'দ্বিতীয় বাক্য। তৃতীয়।',
    })
    expect(firstSentence('First sentence. Second one.')).toEqual({
      first: 'First sentence.',
      rest: 'Second one.',
    })
  })

  it('FR-70 keeps one sentence whole and never splits a number or a section reference', () => {
    expect(firstSentence('Section 108(2) pays 2.5 times.')).toEqual({
      first: 'Section 108(2) pays 2.5 times.',
      rest: '',
    })
    expect(firstSentence('এক বাক্য, কোনো দাঁড়ি ছাড়া')).toEqual({
      first: 'এক বাক্য, কোনো দাঁড়ি ছাড়া',
      rest: '',
    })
  })

  it('FR-70 handles a sentence that ends inside brackets', () => {
    expect(firstSentence('নিয়ম প্রযোজ্য নয় (ধারা ১০৮(২))। পরের কথা।')).toEqual({
      first: 'নিয়ম প্রযোজ্য নয় (ধারা ১০৮(২))।',
      rest: 'পরের কথা।',
    })
  })
})
