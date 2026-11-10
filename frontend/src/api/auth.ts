import { apiGet, apiPost } from './client'
import type { components } from './types'

export type TokenResponse = components['schemas']['TokenResponse']
export type Me = components['schemas']['Me']
export type Role = 'WORKER' | 'HR' | 'ADMIN'
export type DemoRole = 'worker' | 'hr' | 'admin'

export function login(email: string, password: string, language: string): Promise<TokenResponse> {
  return apiPost<TokenResponse>('/api/v1/auth/login', language, { email, password })
}

export function demoLogin(role: DemoRole, language: string): Promise<TokenResponse> {
  return apiPost<TokenResponse>(`/api/v1/auth/demo/${role}`, language)
}

export function logout(accessToken: string, language: string): Promise<undefined> {
  return apiPost<undefined>('/api/v1/auth/logout', language, undefined, accessToken)
}

export function me(accessToken: string, language: string): Promise<Me> {
  return apiGet<Me>('/api/v1/auth/me', language, accessToken)
}

let inFlight: Promise<TokenResponse> | null = null

/**
 * Exchanges the HttpOnly refresh cookie for a new access token. Concurrent calls share one request:
 * a refresh token rotates on use, so a second parallel call would look like token reuse and end the
 * whole session (React StrictMode mounts effects twice in development).
 */
export function refresh(language: string): Promise<TokenResponse> {
  inFlight ??= apiPost<TokenResponse>('/api/v1/auth/refresh', language).finally(() => {
    inFlight = null
  })
  return inFlight
}

export function isRole(value: string): value is Role {
  return value === 'WORKER' || value === 'HR' || value === 'ADMIN'
}
