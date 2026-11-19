import type { components } from './types'
import { ApiError, NetworkError, type Problem } from './errors'
import { isStaticReplay, replayBlob, replayRequest } from './replay'

export type Meta = components['schemas']['Meta']

export { ApiError, NetworkError, type Problem } from './errors'

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
  body?: string | FormData
  headers?: Record<string, string>
}

async function request<T>(path: string, init: RequestOptions, language: string): Promise<T> {
  if (isStaticReplay()) {
    // A static recorded replay has no server: the recorded files answer (ADR-0027).
    return replayRequest<T>(init.method ?? 'GET', path, language, init.body)
  }
  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, {
      ...init,
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json',
        'Accept-Language': language,
        // A string body is JSON; a FormData body sets its own multipart boundary.
        ...(typeof init.body === 'string' ? { 'Content-Type': 'application/json' } : {}),
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

export function apiGet<T>(
  path: string,
  language: string,
  accessToken?: string,
  headers: Record<string, string> = {},
): Promise<T> {
  const auth = bearer(accessToken)
  return request<T>(path, { headers: { ...auth.headers, ...headers } }, language)
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

/** A multipart POST (file upload); the browser adds the boundary to the content type. */
export function apiPostForm<T>(
  path: string,
  language: string,
  form: FormData,
  accessToken?: string,
): Promise<T> {
  return request<T>(path, { method: 'POST', body: form, ...bearer(accessToken) }, language)
}

/** PATCH, PUT or DELETE with an optional JSON body. */
export function apiSend<T>(
  method: 'PATCH' | 'PUT' | 'DELETE',
  path: string,
  language: string,
  body?: unknown,
  accessToken?: string,
): Promise<T> {
  return request<T>(
    path,
    { method, body: body === undefined ? undefined : JSON.stringify(body), ...bearer(accessToken) },
    language,
  )
}

/** The raw response of an authorised request, for downloads such as the memo PDF. */
export async function apiFetchBlob(
  path: string,
  language: string,
  body: unknown,
  accessToken?: string,
): Promise<{ blob: Blob; contentType: string }> {
  if (isStaticReplay()) {
    return replayBlob(path, language, body)
  }
  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Accept-Language': language,
        'Content-Type': 'application/json',
        ...(accessToken === undefined ? {} : { Authorization: `Bearer ${accessToken}` }),
      },
      body: JSON.stringify(body),
    })
  } catch {
    throw new NetworkError()
  }
  if (!response.ok) {
    let problem: Problem | null
    try {
      const parsed: unknown = await response.json()
      problem = isProblem(parsed) ? parsed : null
    } catch {
      problem = null
    }
    throw new ApiError(response.status, problem)
  }
  return { blob: await response.blob(), contentType: response.headers.get('Content-Type') ?? '' }
}

function bearer(accessToken: string | undefined): RequestOptions {
  return accessToken === undefined ? {} : { headers: { Authorization: `Bearer ${accessToken}` } }
}

export function getMeta(language: string): Promise<Meta> {
  return apiGet<Meta>('/api/v1/meta', language)
}
