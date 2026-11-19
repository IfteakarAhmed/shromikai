import { describe, expect, it } from 'vitest'
import {
  assertNoSecrets,
  collectSectionRefs,
  redactLogin,
  redactMeta,
  redactResult,
  sha256,
} from '../../scripts/replay-export-core.mjs'

describe('FR-85 replay exporter core', () => {
  it('FR-85 finds every cited section once, wherever the result cites it', () => {
    const result = {
      findings: [
        { citations: [{ actCode: 'BLA-2006', sectionNo: '3', versionLabel: 'v1', quote: 'q' }] },
        { citations: [{ actCode: 'BLA-2006', sectionNo: '3', versionLabel: 'v1' }] },
        { citations: [{ actCode: 'CA-1872', sectionNo: '27', versionLabel: 'v2' }] },
      ],
      obligations: [{ citations: [{ actCode: 'BLA-2006', sectionNo: '100', versionLabel: 'v1' }] }],
      memo: { sections: [{ text: 'no citation here' }] },
    }
    expect(collectSectionRefs(result)).toEqual([
      { actCode: 'BLA-2006', sectionNo: '100', versionLabel: 'v1' },
      { actCode: 'BLA-2006', sectionNo: '3', versionLabel: 'v1' },
      { actCode: 'CA-1872', sectionNo: '27', versionLabel: 'v2' },
    ])
  })

  it('FR-85 removes only the audit counters from meta and says so', () => {
    const { value, redactions } = redactMeta({
      name: 'x',
      audit: { openReviewItems: 5 },
      recordedDemo: { side: 'WORKER' },
    })
    expect(value).toEqual({ name: 'x', audit: null, recordedDemo: { side: 'WORKER' } })
    expect(redactions).toEqual([
      {
        path: 'audit',
        reason: 'counters read from the audit and review tables of the development database',
      },
    ])
  })

  it('FR-85 replaces the access token of the login response and leaves the rest as it was', () => {
    const { value, redactions } = redactLogin({
      accessToken: 'eyJabc.def.ghi',
      expiresIn: 900,
      guest: false,
      user: { email: 'w@e.t' },
    })
    expect(value).toEqual({
      accessToken: 'static-replay',
      expiresIn: 900,
      guest: false,
      user: { email: 'w@e.t' },
    })
    expect(redactions.map((r) => r.path)).toEqual(['accessToken'])
  })

  it('FR-85 drops the review ticket, whose secret code belongs to the development database', () => {
    const { value, redactions } = redactResult({
      findings: [],
      reviewTicket: { ticketId: 't', token: 'secret', expiresAt: 'x' },
    })
    expect(value).toEqual({ findings: [], reviewTicket: null })
    expect(redactions.map((r) => r.path)).toEqual(['reviewTicket'])
  })

  it('FR-85 does not report a redaction when there was nothing to remove', () => {
    expect(redactResult({ findings: [], reviewTicket: null }).redactions).toEqual([])
    expect(redactMeta({ audit: null }).redactions).toEqual([])
  })

  it('FR-85 refuses a file that holds a token or a refresh secret', () => {
    const jwt = '{"a":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl"}'
    expect(() => {
      assertNoSecrets(jwt, 'x.json')
    }).toThrow(/x\.json/)
    expect(() => {
      assertNoSecrets('{"refreshToken":"abc"}', 'y.json')
    }).toThrow(/y\.json/)
    expect(() => {
      assertNoSecrets('{"signature":"c2ln"}', 'z.json')
    }).not.toThrow()
  })

  it('FR-85 hashes bytes with SHA-256', () => {
    expect(sha256(new TextEncoder().encode('abc'))).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    )
  })
})
