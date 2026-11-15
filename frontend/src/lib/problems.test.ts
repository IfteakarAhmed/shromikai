/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import i18n from '../i18n'
import { ApiError, NetworkError } from '../api/client'
import bn from '../i18n/bn.json'
import en from '../i18n/en.json'
import { describeError, problemCode } from './problems'

function codesOfTheErrorModel(): string[] {
  const spec = readFileSync(resolve(process.cwd(), '..', 'docs', 'spec', 'api.md'), 'utf8')
  const section = spec.slice(spec.indexOf('## 7. Error model'), spec.indexOf('## 8. Rate limits'))
  return [...section.matchAll(/^\| `([a-z-]+)` \| \d+ \|/gm)].map((match) => match[1] ?? '')
}

function problem(code: string, extra: Record<string, unknown> = {}) {
  return new ApiError(400, {
    type: `urn:shromikai:problem:${code}`,
    title: 'x',
    status: 400,
    instance: '/x',
    code,
    traceId: 't',
    ...extra,
  })
}

describe('describeError', () => {
  it('FR-75 has a message in bn and en for every problem code of api.md §7', () => {
    const codes = codesOfTheErrorModel()
    expect(codes.length).toBeGreaterThanOrEqual(40)
    for (const code of codes) {
      expect(bn.problems, code).toHaveProperty(code)
      expect(en.problems, code).toHaveProperty(code)
    }
  })

  it('FR-75 answers in the language of the page', async () => {
    const t = i18n.getFixedT('bn')
    expect(describeError(problem('quota-exceeded'), t, 'bn')).toBe(bn.problems['quota-exceeded'])
    await i18n.changeLanguage('en')
    expect(describeError(problem('quota-exceeded'), i18n.t, 'en')).toBe(
      en.problems['quota-exceeded'],
    )
  })

  it('FR-75 adds the waiting time when the server gives one', () => {
    const message = describeError(
      problem('quota-exceeded', { retryAfterSeconds: 3600 }),
      i18n.getFixedT('en'),
      'en',
    )

    expect(message).toContain(en.problems['quota-exceeded'])
    expect(message).toContain('60')
  })

  it('FR-75 never shows a raw code: unknown codes and network failures get a plain message', () => {
    const t = i18n.getFixedT('en')

    expect(describeError(problem('brand-new-code'), t, 'en')).toBe(en.problems.unknown)
    expect(describeError(new NetworkError(), t, 'en')).toBe(en.problems.network)
    expect(describeError(new Error('boom'), t, 'en')).toBe(en.problems.unknown)
  })

  it('FR-69 reads the problem code of an API error and nothing else', () => {
    expect(problemCode(problem('not-recorded'))).toBe('not-recorded')
    expect(problemCode(new Error('x'))).toBeNull()
  })
})
