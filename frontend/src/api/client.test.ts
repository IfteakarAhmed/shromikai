import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { ApiError, NetworkError, apiGet, getMeta } from './client'
import { server } from '../test/msw/server'
import { formatNumber } from '../lib/format'

describe('api client', () => {
  it('FR-01 returns the typed meta document and sends the language', async () => {
    let language: string | null = null
    server.use(
      http.get('/api/v1/meta', ({ request }) => {
        language = request.headers.get('Accept-Language')
        return HttpResponse.json({ name: 'ShromikAI' })
      }),
    )

    const meta = await getMeta('en')

    expect(meta.name).toBe('ShromikAI')
    expect(language).toBe('en')
  })

  it('FR-02 turns a problem document into an ApiError', async () => {
    server.use(
      http.get('/api/v1/x', () =>
        HttpResponse.json(
          {
            type: 'urn:shromikai:problem:not-found',
            title: 'পাওয়া যায়নি',
            status: 404,
            instance: '/x',
            code: 'not-found',
            traceId: 't',
          },
          { status: 404 },
        ),
      ),
    )

    const error = await apiGet('/api/v1/x', 'bn').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).problem?.code).toBe('not-found')
    expect((error as ApiError).status).toBe(404)
  })

  it('FR-02 keeps the status when the error body is not a problem', async () => {
    server.use(http.get('/api/v1/y', () => new HttpResponse('oops', { status: 502 })))

    const error = await apiGet('/api/v1/y', 'bn').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).problem).toBeNull()
    expect((error as ApiError).message).toBe('http-502')
  })

  it('FR-02 reports a network error when the backend is unreachable', async () => {
    server.use(http.get('/api/v1/z', () => HttpResponse.error()))

    await expect(apiGet('/api/v1/z', 'bn')).rejects.toBeInstanceOf(NetworkError)
  })

  it('NFR-09 formats numbers with Bangla digits for bn', () => {
    expect(formatNumber(1234, 'bn')).toBe('১,২৩৪')
    expect(formatNumber(1234, 'en')).toBe('1,234')
  })
})
