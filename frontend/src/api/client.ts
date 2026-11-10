import type { components } from './types'

export type Meta = components['schemas']['Meta']

/** RFC 9457 problem document (api.md §5.14), generated from the OpenAPI document. */
export type Problem = components['schemas']['Problem']

/** The backend answered with a problem document or an unexpected status. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem | null

  constructor(status: number, problem: Problem | null) {
    super(problem?.code ?? `http-${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

/** The backend could not be reached at all. */
export class NetworkError extends Error {
  constructor() {
    super('network-error')
    this.name = 'NetworkError'
  }
}

const BASE: string = import.meta.env.VITE_API_BASE ?? ''

function isProblem(value: unknown): value is Problem {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as Problem).code === 'string' &&
    typeof (value as Problem).title === 'string'
  )
}

interface RequestOptions {
  method?: string
  body?: string
  headers?: Record<string, string>
}

async function request<T>(path: string, init: RequestOptions, language: string): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, {
      ...init,
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json',
        'Accept-Language': language,
        ...(init.body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...init.headers,
      },
    })
  } catch {
    throw new NetworkError()
  }
  if (!response.ok) {
    let problem: Problem | null
    try {
      const body: unknown = await response.json()
      problem = isProblem(body) ? body : null
    } catch {
      problem = null
    }
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function apiGet<T>(path: string, language: string, accessToken?: string): Promise<T> {
  return request<T>(path, bearer(accessToken), language)
}

/** POST with an optional JSON body. The access token goes in the header, never in the URL. */
export function apiPost<T>(
  path: string,
  language: string,
  body?: unknown,
  accessToken?: string,
): Promise<T> {
  return request<T>(
    path,
    {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
      ...bearer(accessToken),
    },
    language,
  )
}

function bearer(accessToken: string | undefined): RequestOptions {
  return accessToken === undefined ? {} : { headers: { Authorization: `Bearer ${accessToken}` } }
}

export function getMeta(language: string): Promise<Meta> {
  return apiGet<Meta>('/api/v1/meta', language)
}
