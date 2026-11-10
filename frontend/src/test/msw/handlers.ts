import { http, HttpResponse } from 'msw'
import type { Me, TokenResponse } from '../../api/auth'
import type { Meta } from '../../api/client'

/** Synthetic Meta response for the fictional test corpus. */
export const sampleMeta: Meta = {
  name: 'ShromikAI',
  version: '0.1.0',
  models: {
    reasoning: 'claude-sonnet-5-5',
    fast: 'claude-haiku-5-5',
    verifier: null,
    verifierFallback: 'claude-haiku-5-5',
    judge: null,
    localPii: 'qwen2.5:7b-instruct',
    embedding: 'bge-m3',
  },
  corpus: {
    release: null,
    acts: 3,
    sections: 120,
    currentSections: 112,
    embeddedSections: 112,
    lastRefreshAt: null,
  },
  audit: null,
}

/** Synthetic account for a role; every value is fictional. */
export function sampleUser(role: Me['role']): Me {
  return {
    userId: '00000000-0000-4000-8000-000000000001',
    email: `${role.toLowerCase()}@example.test`,
    role,
    org: null,
    locale: 'bn',
    profile: null,
    demo: false,
  }
}

export function tokenResponse(role: Me['role'], demo = false): TokenResponse {
  return {
    accessToken: `access-token-for-${role}`,
    expiresIn: 900,
    tokenType: 'Bearer',
    guest: false,
    user: { ...sampleUser(role), demo },
  }
}

export function problem(status: number, code: string, title: string, extra = {}) {
  return HttpResponse.json(
    {
      type: `urn:shromikai:problem:${code}`,
      title,
      status,
      instance: `/test#r1`,
      code,
      traceId: 'r1',
      ...extra,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

export const handlers = [
  http.get('/api/v1/meta', () => HttpResponse.json(sampleMeta)),
  // No session by default: the silent refresh at start finds no refresh cookie.
  http.post('/api/v1/auth/refresh', () => problem(401, 'invalid-token', 'সাইন-ইনের মেয়াদ শেষ')),
]
