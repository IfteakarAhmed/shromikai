import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '../test/msw/server'
import { parseSseStream, watchEvents, type WatchEventsOptions, type WatchOutcome } from './sse'

const ID = '11111111-1111-4111-8111-111111111111'
const URL = `/api/v1/analyses/${ID}/events`

function sse(...frames: string[]): Response {
  const encoder = new TextEncoder()
  return new HttpResponse(
    new ReadableStream({
      start(controller) {
        for (const frame of frames) {
          controller.enqueue(encoder.encode(frame))
        }
        controller.close()
      },
    }),
    { headers: { 'Content-Type': 'text/event-stream' } },
  )
}

function stage(id: number, stageName: string, progress: number): string {
  const data = JSON.stringify({
    analysisId: ID,
    stage: stageName,
    state: 'COMPLETED',
    progress,
    message: 'm',
    at: '2026-11-16T10:00:00.000Z',
  })
  return `id: ${id}\nevent: stage\ndata: ${data}\n\n`
}

function completed(id: number): string {
  const data = JSON.stringify({
    analysisId: ID,
    resultUrl: `/api/v1/analyses/${ID}`,
    findingCount: 3,
    uncertainCount: 1,
  })
  return `id: ${id}\nevent: completed\ndata: ${data}\n\n`
}

function options(overrides: Partial<WatchEventsOptions> = {}): WatchEventsOptions {
  return {
    url: URL,
    language: 'bn',
    getToken: () => 'token-1',
    refreshToken: () => Promise.resolve('token-2'),
    poll: () => Promise.resolve({ state: 'running', stage: null, progress: 0 }),
    onEvent: () => undefined,
    pollIntervalMs: 5,
    retryDelayMs: 1,
    ...overrides,
  }
}

async function collect(overrides: Partial<WatchEventsOptions> = {}) {
  const events: string[] = []
  const outcome = await watchEvents(
    options({
      ...overrides,
      onEvent: (event) => {
        events.push(`${event.name}:${event.id}`)
      },
    }),
  )
  return { events, outcome }
}

describe('parseSseStream', () => {
  it('FR-58 parses frames split anywhere, skips keep-alive comments and reads CRLF', async () => {
    const encoder = new TextEncoder()
    const text =
      ': keep-alive\r\n\r\nid: 1\r\nevent: stage\r\ndata: {"a":1}\r\n\r\nid: 2\nevent: completed\ndata: {"b":2}\n\n'
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        for (let i = 0; i < text.length; i += 7) {
          controller.enqueue(encoder.encode(text.slice(i, i + 7)))
        }
        controller.close()
      },
    })

    const frames = []
    for await (const frame of parseSseStream(body)) {
      frames.push(frame)
    }

    expect(frames).toEqual([
      { id: 1, name: 'stage', data: '{"a":1}' },
      { id: 2, name: 'completed', data: '{"b":2}' },
    ])
  })

  it('FR-58 joins several data lines with a newline', async () => {
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('id: 3\nevent: warning\ndata: a\ndata: b\n\n'))
        controller.close()
      },
    })

    const frames = []
    for await (const frame of parseSseStream(body)) {
      frames.push(frame)
    }

    expect(frames).toEqual([{ id: 3, name: 'warning', data: 'a\nb' }])
  })
})

describe('watchEvents', () => {
  it('FR-58 delivers the events in order and ends on completed', async () => {
    server.use(
      http.get(URL, () => sse(stage(1, 'INTAKE', 5), stage(2, 'REDACT', 25), completed(3))),
    )

    const { events, outcome } = await collect()

    expect(events).toEqual(['stage:1', 'stage:2', 'completed:3'])
    expect(outcome.kind).toBe('completed')
  })

  it('T-23 sends the token and the language in headers and never puts a token in the URL', async () => {
    let seen: Request | null = null
    server.use(
      http.get(URL, ({ request }) => {
        seen = request
        return sse(completed(1))
      }),
    )

    await collect({ getToken: () => 'secret-access-token' })

    const request = seen as Request | null
    expect(request?.headers.get('Authorization')).toBe('Bearer secret-access-token')
    expect(request?.headers.get('Accept')).toBe('text/event-stream')
    expect(request?.headers.get('Accept-Language')).toBe('bn')
    expect(request?.url).not.toMatch(/token|access_token|api_key/i)
    expect(request?.url).not.toContain('secret-access-token')
  })

  it('FR-58 reconnects with Last-Event-ID after the stream drops, without repeating events', async () => {
    const lastIds: (string | null)[] = []
    server.use(
      http.get(URL, ({ request }) => {
        lastIds.push(request.headers.get('Last-Event-ID'))
        return lastIds.length === 1
          ? sse(stage(1, 'INTAKE', 5), stage(2, 'REDACT', 25))
          : sse(stage(3, 'RISK', 65), completed(4))
      }),
    )

    const { events, outcome } = await collect()

    expect(lastIds).toEqual([null, '2'])
    expect(events).toEqual(['stage:1', 'stage:2', 'stage:3', 'completed:4'])
    expect(outcome.kind).toBe('completed')
  })

  it('FR-05 refreshes the token once on 401 and retries with the new one', async () => {
    const auth: (string | null)[] = []
    server.use(
      http.get(URL, ({ request }) => {
        auth.push(request.headers.get('Authorization'))
        return auth.length === 1 ? new HttpResponse(null, { status: 401 }) : sse(completed(1))
      }),
    )
    const refresh = vi.fn(() => Promise.resolve('token-2'))

    const { outcome } = await collect({ refreshToken: refresh })

    expect(auth).toEqual(['Bearer token-1', 'Bearer token-2'])
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(outcome.kind).toBe('completed')
  })

  it('FR-05 gives up on a second 401 instead of looping', async () => {
    let calls = 0
    server.use(
      http.get(URL, () => {
        calls += 1
        return new HttpResponse(null, { status: 401 })
      }),
    )
    const refresh = vi.fn(() => Promise.resolve('token-2'))

    const { outcome } = await collect({ refreshToken: refresh })

    expect(calls).toBe(2)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(outcome.kind).toBe('unauthorised')
  })

  it('FR-58 falls back to polling every few seconds after two failures', async () => {
    let calls = 0
    server.use(
      http.get(URL, () => {
        calls += 1
        return new HttpResponse(null, { status: 503 })
      }),
    )
    const polls: string[] = []
    let n = 0

    const { outcome } = await collect({
      poll: () => {
        n += 1
        polls.push(`poll${n}`)
        return Promise.resolve(
          n < 3
            ? { state: 'running', stage: 'RISK', progress: 65 }
            : { state: 'completed', findingCount: 4, uncertainCount: 1 },
        )
      },
    })

    expect(calls).toBe(2)
    expect(polls).toEqual(['poll1', 'poll2', 'poll3'])
    expect(outcome).toEqual<WatchOutcome>({
      kind: 'completed',
      via: 'polling',
      findingCount: 4,
      uncertainCount: 1,
    })
  })

  it('FR-58 reports progress while polling', async () => {
    server.use(http.get(URL, () => new HttpResponse(null, { status: 500 })))
    let n = 0

    const { events: seen } = await collect({
      poll: () => {
        n += 1
        return Promise.resolve(
          n < 2
            ? { state: 'running', stage: 'VERIFY', progress: 80 }
            : { state: 'completed', findingCount: 1, uncertainCount: 0 },
        )
      },
    })

    expect(seen).toContain('stage:0')
  })

  it('FR-58 ends with the problem when the server says the analysis failed', async () => {
    const problem = JSON.stringify({
      type: 'urn:shromikai:problem:analysis-timeout',
      title: 'x',
      status: 504,
      instance: '/x',
      code: 'analysis-timeout',
      traceId: 't',
    })
    server.use(http.get(URL, () => sse(`id: 1\nevent: failed\ndata: ${problem}\n\n`)))

    const { outcome } = await collect()

    expect(outcome.kind).toBe('failed')
    if (outcome.kind === 'failed') {
      expect(outcome.problem?.code).toBe('analysis-timeout')
    }
  })

  it('FR-58 treats 404 and 410 as final, with no retry and no polling', async () => {
    let calls = 0
    server.use(
      http.get(URL, () => {
        calls += 1
        return new HttpResponse(null, { status: 410 })
      }),
    )

    const { outcome } = await collect()

    expect(calls).toBe(1)
    expect(outcome.kind).toBe('gone')
  })

  it('FR-58 stops when the caller aborts', async () => {
    server.use(http.get(URL, () => new Promise<Response>(() => undefined)))
    const controller = new AbortController()

    const pending = watchEvents(options({ signal: controller.signal }))
    controller.abort()

    await expect(pending).resolves.toEqual({ kind: 'aborted' })
  })
})
