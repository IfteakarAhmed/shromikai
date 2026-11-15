import type { components } from './types'
import type { Problem } from './client'

/**
 * A fetch-based Server-Sent Events client (architecture §17, T-23). `EventSource` cannot send an
 * `Authorization` header and the API never accepts a token in the URL, so the stream is read with
 * `fetch`: the token travels in a header, a dropped stream is resumed with `Last-Event-ID`, an
 * expired token is renewed once, and after two failures the client falls back to polling.
 */

export type Stage = components['schemas']['SseStageEvent']['stage']

/** One analysis event as the UI uses it (already checked against the schemas of api.md §4.2). */
export type WatchEvent =
  | { id: number; name: 'stage'; stage: Stage; progress: number; message: string | null }
  | { id: number; name: 'warning'; code: string; message: string }
  | { id: number; name: 'completed'; findingCount: number; uncertainCount: number }
  | { id: number; name: 'failed'; problem: Problem | null }

export type PollResult =
  | { state: 'running'; stage: Stage | null; progress: number }
  | { state: 'completed'; findingCount: number; uncertainCount: number }
  | { state: 'failed'; problem: Problem | null }
  | { state: 'gone' }

export type WatchOutcome =
  | { kind: 'completed'; via: 'stream' | 'polling'; findingCount: number; uncertainCount: number }
  | { kind: 'failed'; problem: Problem | null }
  | { kind: 'gone' }
  | { kind: 'unauthorised' }
  | { kind: 'aborted' }

export interface WatchEventsOptions {
  /** Path of the events endpoint, for example `/api/v1/analyses/{id}/events`. */
  url: string
  language: string
  getToken: () => string | null
  /** A new access token, or null when the session cannot be renewed. Called at most once. */
  refreshToken: () => Promise<string | null>
  /** One look at the job (GET the analysis), used after two stream failures. */
  poll: () => Promise<PollResult>
  onEvent: (event: WatchEvent) => void
  signal?: AbortSignal
  pollIntervalMs?: number
  retryDelayMs?: number
}

export interface SseFrame {
  id: number
  name: string
  data: string
}

const BASE: string = import.meta.env.VITE_API_BASE ?? ''
const STREAM_FAILURES_BEFORE_POLLING = 2

/** Splits an event stream into frames; comments (keep-alives) are skipped. */
export async function* parseSseStream(body: ReadableStream<Uint8Array>): AsyncGenerator<SseFrame> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let id = 0
  let name = 'message'
  let data: string[] = []

  function* flush(): Generator<SseFrame> {
    if (data.length > 0) {
      yield { id, name, data: data.join('\n') }
    }
    name = 'message'
    data = []
  }

  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      let newline = buffer.indexOf('\n')
      while (newline >= 0) {
        const line = buffer.slice(0, newline).replace(/\r$/, '')
        buffer = buffer.slice(newline + 1)
        if (line === '') {
          yield* flush()
        } else if (!line.startsWith(':')) {
          const colon = line.indexOf(':')
          const field = colon < 0 ? line : line.slice(0, colon)
          const raw = colon < 0 ? '' : line.slice(colon + 1)
          const content = raw.startsWith(' ') ? raw.slice(1) : raw
          if (field === 'id') {
            const parsed = Number.parseInt(content, 10)
            id = Number.isNaN(parsed) ? id : parsed
          } else if (field === 'event') {
            name = content
          } else if (field === 'data') {
            data.push(content)
          }
        }
        newline = buffer.indexOf('\n')
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isProblem(value: unknown): value is Problem {
  return isRecord(value) && typeof value['code'] === 'string' && typeof value['title'] === 'string'
}

/** A frame as a typed event, or null for an event this client does not know or a bad payload. */
export function toWatchEvent(frame: SseFrame): WatchEvent | null {
  let payload: unknown
  try {
    payload = JSON.parse(frame.data)
  } catch {
    return null
  }
  if (!isRecord(payload)) {
    return null
  }
  switch (frame.name) {
    case 'stage':
      return typeof payload['stage'] === 'string' && typeof payload['progress'] === 'number'
        ? {
            id: frame.id,
            name: 'stage',
            stage: payload['stage'] as Stage,
            progress: payload['progress'],
            message: typeof payload['message'] === 'string' ? payload['message'] : null,
          }
        : null
    case 'warning':
      return typeof payload['code'] === 'string' && typeof payload['message'] === 'string'
        ? { id: frame.id, name: 'warning', code: payload['code'], message: payload['message'] }
        : null
    case 'completed':
      return typeof payload['findingCount'] === 'number' &&
        typeof payload['uncertainCount'] === 'number'
        ? {
            id: frame.id,
            name: 'completed',
            findingCount: payload['findingCount'],
            uncertainCount: payload['uncertainCount'],
          }
        : null
    case 'failed':
      return { id: frame.id, name: 'failed', problem: isProblem(payload) ? payload : null }
    default:
      return null
  }
}

function isAborted(signal: AbortSignal | undefined): boolean {
  return signal?.aborted === true
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (isAborted(signal)) {
      resolve()
      return
    }
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timer)
        resolve()
      },
      { once: true },
    )
  })
}

/**
 * Follows an analysis until it completes or fails. Resolves with how it ended; it never rejects for
 * a network or server problem, because a user watching a progress bar needs an outcome, not a
 * stack trace.
 */
export async function watchEvents(options: WatchEventsOptions): Promise<WatchOutcome> {
  const { signal } = options
  const retryDelay = options.retryDelayMs ?? 700
  let token = options.getToken()
  let lastId = 0
  let failures = 0
  let refreshed = false

  while (failures < STREAM_FAILURES_BEFORE_POLLING) {
    if (isAborted(signal)) {
      return { kind: 'aborted' }
    }
    try {
      const headers: Record<string, string> = {
        Accept: 'text/event-stream',
        'Accept-Language': options.language,
      }
      if (token !== null) {
        headers['Authorization'] = `Bearer ${token}`
      }
      if (lastId > 0) {
        headers['Last-Event-ID'] = String(lastId)
      }
      const response = await fetch(`${BASE}${options.url}`, {
        headers,
        credentials: 'same-origin',
        signal,
      })
      if (response.status === 401) {
        if (refreshed) {
          return { kind: 'unauthorised' }
        }
        refreshed = true
        token = await options.refreshToken()
        if (token === null) {
          return { kind: 'unauthorised' }
        }
        continue
      }
      if (response.status === 404 || response.status === 410) {
        return { kind: 'gone' }
      }
      if (!response.ok || response.body === null) {
        failures += 1
        await sleep(retryDelay * failures, signal)
        continue
      }
      for await (const frame of parseSseStream(response.body)) {
        const event = toWatchEvent(frame)
        if (event === null) {
          continue
        }
        lastId = Math.max(lastId, event.id)
        options.onEvent(event)
        if (event.name === 'completed') {
          return {
            kind: 'completed',
            via: 'stream',
            findingCount: event.findingCount,
            uncertainCount: event.uncertainCount,
          }
        }
        if (event.name === 'failed') {
          return { kind: 'failed', problem: event.problem }
        }
      }
      // The stream ended without a final event: resume from the last event.
      failures += 1
      await sleep(retryDelay, signal)
    } catch {
      if (isAborted(signal)) {
        return { kind: 'aborted' }
      }
      failures += 1
      await sleep(retryDelay * failures, signal)
    }
  }
  return poll(options)
}

async function poll(options: WatchEventsOptions): Promise<WatchOutcome> {
  const interval = options.pollIntervalMs ?? 3000
  for (;;) {
    if (isAborted(options.signal)) {
      return { kind: 'aborted' }
    }
    try {
      const result = await options.poll()
      switch (result.state) {
        case 'completed':
          return {
            kind: 'completed',
            via: 'polling',
            findingCount: result.findingCount,
            uncertainCount: result.uncertainCount,
          }
        case 'failed':
          return { kind: 'failed', problem: result.problem }
        case 'gone':
          return { kind: 'gone' }
        case 'running':
          if (result.stage !== null) {
            options.onEvent({
              id: 0,
              name: 'stage',
              stage: result.stage,
              progress: result.progress,
              message: null,
            })
          }
      }
    } catch {
      // A failed look is not the end; try again after the interval.
    }
    await sleep(interval, options.signal)
  }
}
