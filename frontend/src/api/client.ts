import type { components } from './types'

export type Meta = components['schemas']['Meta']

/** RFC 9457 problem document (api.md §5.14). */
export interface Problem {
  type: string
  title: string
  status: number
  detail?: string | null
  instance: string
  code: string
  traceId: string
}

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

export async function apiGet<T>(path: string, language: string): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, {
      headers: { Accept: 'application/json', 'Accept-Language': language },
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
  return (await response.json()) as T
}

export function getMeta(language: string): Promise<Meta> {
  return apiGet<Meta>('/api/v1/meta', language)
}
