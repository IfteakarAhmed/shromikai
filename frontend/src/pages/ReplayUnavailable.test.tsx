import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { server } from '../test/msw/server'
import { renderApp } from '../test/render'

beforeEach(() => {
  vi.stubEnv('VITE_STATIC_REPLAY', '1')
  // The pages under /app first open the recorded demo session (a guest gets it).
  server.use(
    http.get('/replay/login.worker.json', () =>
      HttpResponse.json({
        accessToken: 'stub',
        expiresIn: 900,
        guest: false,
        tokenType: 'Bearer',
        user: { id: 'u', email: 'w@example.test', role: 'WORKER' },
      }),
    ),
    http.get('/replay/*', () => new HttpResponse(null, { status: 404 })),
  )
})
afterEach(() => {
  vi.unstubAllEnvs()
})

describe('FR-85 features a static replay cannot offer', () => {
  it.each(['/login', '/demo-hr', '/demo-admin', '/app/compare', '/app/profile', '/admin'])(
    'FR-85 %s says plainly that it needs a live server and points to the sample',
    async (path) => {
      renderApp(<App />, path)
      expect(
        await screen.findByRole('heading', { name: 'এটি স্ট্যাটিক রেকর্ডকৃত রিপ্লেতে নেই' }),
      ).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'নমুনা চুক্তির রিপ্লে দেখুন' })).toHaveAttribute(
        'href',
        '/demo-user',
      )
    },
  )
})
