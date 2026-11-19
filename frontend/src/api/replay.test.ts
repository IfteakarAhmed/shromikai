import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { ApiError, apiFetchBlob, apiGet, apiPost, apiPostForm, apiSend } from './client'
import { isStaticReplay, resetReplaySession } from './replay'
import { server } from '../test/msw/server'

/** Test doubles for the exported files; the real files come from scripts/export-replay.mjs. */
const FILES: Record<string, Record<string, unknown>> = {
  'meta.bn.json': {
    name: 'ShromikAI',
    lang: 'bn',
    recordedDemo: { side: 'WORKER', outputLanguage: 'bn' },
  },
  'meta.en.json': {
    name: 'ShromikAI',
    lang: 'en',
    recordedDemo: { side: 'WORKER', outputLanguage: 'bn' },
  },
  'login.worker.json': {
    accessToken: 'stub',
    expiresIn: 900,
    guest: false,
    tokenType: 'Bearer',
    user: { email: 'w@example.test' },
  },
  'analysis-accepted.json': {
    analysisId: 'a-1',
    status: 'QUEUED',
    eventsUrl: '/api/v1/analyses/a-1/events',
    resultUrl: '/api/v1/analyses/a-1',
    deadlineAt: '2026-11-19T00:00:00Z',
  },
  'analysis.bn.json': { analysisId: 'a-1', status: 'SUCCEEDED', findings: [{ id: 'f1' }] },
  'analysis.en.json': { analysisId: 'a-1', status: 'SUCCEEDED', findings: [{ id: 'f1' }] },
  'trace.bn.json': { analysisId: 'a-1', events: [] },
  'trace.en.json': { analysisId: 'a-1', events: [] },
  'laws/BLA-2006_3_v1.bn.json': { sectionKey: 'BLA-2006:3@v1' },
}
const PDF = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d]) // %PDF-

const requested: string[] = []

beforeEach(() => {
  vi.stubEnv('VITE_STATIC_REPLAY', '1')
  resetReplaySession()
  requested.length = 0
  server.use(
    http.get('/replay/*', ({ request }) => {
      const file = new URL(request.url).pathname.slice('/replay/'.length)
      requested.push(file)
      if (file.endsWith('.pdf')) {
        return new HttpResponse(PDF, { headers: { 'Content-Type': 'application/pdf' } })
      }
      return file in FILES
        ? HttpResponse.json(FILES[file])
        : new HttpResponse(null, { status: 404 })
    }),
  )
})
afterEach(() => {
  vi.unstubAllEnvs()
})

async function problemOf(promise: Promise<unknown>): Promise<ApiError> {
  const error = await promise.then(
    () => null,
    (e: unknown) => e,
  )
  expect(error).toBeInstanceOf(ApiError)
  return error as ApiError
}

describe('FR-85 static replay API layer', () => {
  it('FR-85 is on only when VITE_STATIC_REPLAY is 1', () => {
    expect(isStaticReplay()).toBe(true)
    vi.stubEnv('VITE_STATIC_REPLAY', '')
    expect(isStaticReplay()).toBe(false)
  })

  it("FR-85 serves the meta document of the reader's language from the exported files", async () => {
    const meta = await apiGet<{ lang: string }>('/api/v1/meta', 'en')
    expect(meta.lang).toBe('en')
    expect(requested).toEqual(['meta.en.json'])
  })

  it('FR-85 signs in the demo worker with the recorded login response and no network call to /api', async () => {
    const tokens = await apiPost<{ user: { email: string } }>('/api/v1/auth/demo/worker', 'bn')
    expect(tokens.user.email).toBe('w@example.test')
    expect(requested).toEqual(['login.worker.json'])
  })

  it('FR-85 answers the refresh with 401 until someone has signed in, and again after sign-out', async () => {
    expect((await problemOf(apiPost('/api/v1/auth/refresh', 'bn'))).status).toBe(401)
    await apiPost('/api/v1/auth/demo/worker', 'bn')
    await expect(apiPost('/api/v1/auth/refresh', 'bn')).resolves.toMatchObject({ guest: false })
    await apiPost('/api/v1/auth/logout', 'bn', undefined, 'stub')
    expect((await problemOf(apiPost('/api/v1/auth/refresh', 'bn'))).status).toBe(401)
  })

  it('FR-85 gives a guest the recorded demo session, since only one session was recorded', async () => {
    await expect(apiPost('/api/v1/auth/guest', 'bn', { deviceToken: 'x' })).resolves.toMatchObject({
      accessToken: 'stub',
    })
  })

  it('FR-85 refuses the HR and admin demo logins and password logins with an honest problem', async () => {
    for (const path of ['/api/v1/auth/demo/hr', '/api/v1/auth/demo/admin']) {
      const error = await problemOf(apiPost(path, 'bn'))
      expect(error.status).toBe(501)
      expect(error.problem?.code).toBe('static-replay-unavailable')
    }
    const login = await problemOf(
      apiPost('/api/v1/auth/login', 'bn', { email: 'a', password: 'b' }),
    )
    expect(login.problem?.code).toBe('static-replay-unavailable')
  })

  it('FR-85 starts the recorded sample and refuses any other document', async () => {
    const sample = new FormData()
    sample.append('options', new Blob(['{"sample":true}'], { type: 'application/json' }))
    const accepted = await apiPostForm<{ analysisId: string }>(
      '/api/v1/analyses',
      'bn',
      sample,
      'stub',
    )
    expect(accepted.analysisId).toBe('a-1')

    for (const part of ['text', 'file', 'images']) {
      const other = new FormData()
      other.append('options', new Blob(['{"sample":false}'], { type: 'application/json' }))
      other.append(part, new Blob(['x']), 'x.txt')
      const error = await problemOf(apiPostForm('/api/v1/analyses', 'bn', other, 'stub'))
      expect(error.problem?.code).toBe('static-replay-unavailable')
    }
  })

  it('FR-85 returns the recorded result for the recorded analysis and not found for another id', async () => {
    const result = await apiGet<{ status: string }>('/api/v1/analyses/a-1', 'bn', 'stub')
    expect(result.status).toBe('SUCCEEDED')
    const missing = await problemOf(apiGet('/api/v1/analyses/other', 'bn', 'stub'))
    expect(missing.status).toBe(404)
  })

  it('FR-85 returns the recorded trace for the why sheet', async () => {
    await expect(apiGet('/api/v1/analyses/a-1/trace', 'en', 'stub')).resolves.toMatchObject({
      analysisId: 'a-1',
    })
    expect(requested).toContain('trace.en.json')
  })

  it('FR-85 returns a recorded section for a cited section and not found for any other', async () => {
    await expect(apiGet('/api/v1/laws/BLA-2006/3?version=v1', 'bn', 'stub')).resolves.toMatchObject(
      { sectionKey: 'BLA-2006:3@v1' },
    )
    const other = await problemOf(apiGet('/api/v1/laws/BLA-2006/99?version=v1', 'bn', 'stub'))
    expect(other.status).toBe(404)
  })

  it("FR-85 serves the recorded memo PDF of the reader's language", async () => {
    const memo = await apiFetchBlob('/api/v1/memos/render', 'bn', { format: 'PDF' }, 'stub')
    expect(memo.contentType).toBe('application/pdf')
    expect(memo.blob.size).toBe(PDF.length)
    expect(requested).toContain('memo.bn.pdf')
  })

  it('FR-85 refuses compare, playbooks, admin, account changes and every other live call', async () => {
    const calls: Promise<unknown>[] = [
      apiPost('/api/v1/compare', 'bn', { sample: true }, 'stub'),
      apiGet('/api/v1/compare/a-1', 'bn', 'stub'),
      apiGet('/api/v1/playbooks?size=100', 'bn', 'stub'),
      apiGet('/api/v1/admin/api-keys', 'bn', 'stub'),
      apiSend('PATCH', '/api/v1/auth/me', 'bn', { profile: null }, 'stub'),
      apiSend('DELETE', '/api/v1/analyses/a-1', 'bn', undefined, 'stub'),
      apiGet('/api/v1/review-tickets/t', 'bn', 'stub'),
    ]
    for (const call of calls) {
      const error = await problemOf(call)
      expect(error.status).toBe(501)
      expect(error.problem?.code).toBe('static-replay-unavailable')
    }
  })

  it('FR-85 a missing exported file is reported as a problem, not as a network failure', async () => {
    server.use(http.get('/replay/meta.bn.json', () => new HttpResponse(null, { status: 404 })))
    const error = await problemOf(apiGet('/api/v1/meta', 'bn'))
    expect(error.status).toBe(501)
  })

  it('FR-85 does not touch /api when it is off: the normal client path is unchanged', async () => {
    vi.stubEnv('VITE_STATIC_REPLAY', '')
    server.use(http.get('/api/v1/meta', () => HttpResponse.json({ name: 'live' })))
    await expect(apiGet<{ name: string }>('/api/v1/meta', 'bn')).resolves.toEqual({ name: 'live' })
    expect(requested).toEqual([])
  })
})
