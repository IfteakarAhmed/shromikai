import type { components } from './types'

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
