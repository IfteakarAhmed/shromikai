import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import App from '../App'
import { problem, tokenResponse } from '../test/msw/handlers'
import { server } from '../test/msw/server'
import { renderApp } from '../test/render'

async function signIn(email = 'worker@example.test') {
  await userEvent.type(await screen.findByLabelText('ইমেইল'), email)
  await userEvent.type(screen.getByLabelText('পাসওয়ার্ড'), 'secret-secret-1')
  await userEvent.click(screen.getByRole('button', { name: 'সাইন ইন করুন' }))
}

describe('session', () => {
  it('FR-06 redirects a visitor without a session from /admin to the login page', async () => {
    renderApp(<App />, '/admin')

    expect(await screen.findByRole('heading', { name: 'সাইন ইন' })).toBeInTheDocument()
    expect(screen.queryByText('প্রশাসনের পাতা শীঘ্রই আসছে।')).not.toBeInTheDocument()
  })

  it('FR-05 logs in and returns to the page that was asked for', async () => {
    server.use(http.post('/api/v1/auth/login', () => HttpResponse.json(tokenResponse('ADMIN'))))
    renderApp(<App />, '/admin')

    await signIn('admin@example.test')

    expect(await screen.findByText('প্রশাসনের পাতা শীঘ্রই আসছে।')).toBeInTheDocument()
    expect(screen.getByText(/admin@example.test হিসেবে ঢুকেছেন/)).toBeInTheDocument()
  })

  it('FR-09 opens /app to a visitor without a session by starting a guest session', async () => {
    let device: unknown = null
    server.use(
      http.post('/api/v1/auth/guest', async ({ request }) => {
        device = ((await request.json()) as { deviceToken?: string }).deviceToken
        return HttpResponse.json({
          accessToken: 'guest-token',
          expiresIn: 7200,
          tokenType: 'Bearer',
          guest: true,
          user: null,
        })
      }),
    )

    renderApp(<App />, '/app')

    expect(await screen.findByRole('heading', { name: 'আপনার চুক্তি দিন' })).toBeInTheDocument()
    expect(device).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(window.localStorage.getItem('shromikai.device')).toBe(device)
  })

  it('FR-09 a guest cannot open the admin console', async () => {
    server.use(
      http.post('/api/v1/auth/guest', () =>
        HttpResponse.json({
          accessToken: 'guest-token',
          expiresIn: 7200,
          tokenType: 'Bearer',
          guest: true,
          user: null,
        }),
      ),
    )
    renderApp(<App />, '/app')
    await screen.findByRole('heading', { name: 'আপনার চুক্তি দিন' })

    // Same session, a different route: a guest is not an admin.
    const { unmount } = renderApp(<App />, '/admin')

    expect(await screen.findByRole('heading', { name: 'সাইন ইন' })).toBeInTheDocument()
    unmount()
  })

  it('FR-05 restores the session at start through the refresh cookie', async () => {
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(tokenResponse('HR'))))

    renderApp(<App />, '/app')

    expect(await screen.findByRole('heading', { name: 'আপনার চুক্তি দিন' })).toBeInTheDocument()
    expect(screen.getByText(/এইচআর/)).toBeInTheDocument()
  })

  it('FR-08 a login that finishes first is not wiped by the slower silent refresh at start', async () => {
    server.use(
      http.post('/api/v1/auth/refresh', async () => {
        await delay(150)
        return problem(401, 'invalid-token', 'সাইন-ইনের মেয়াদ শেষ')
      }),
      http.post('/api/v1/auth/demo/worker', () => HttpResponse.json(tokenResponse('WORKER', true))),
    )

    renderApp(<App />, '/demo-user')

    expect(await screen.findByRole('heading', { name: 'আপনার চুক্তি দিন' })).toBeInTheDocument()
    await delay(250)
    expect(screen.getByRole('heading', { name: 'আপনার চুক্তি দিন' })).toBeInTheDocument()
  })

  it('FR-05 shows the login error from the server and stays on the form', async () => {
    server.use(
      http.post('/api/v1/auth/login', () =>
        problem(401, 'invalid-credentials', 'ইমেইল বা পাসওয়ার্ড ভুল'),
      ),
    )
    renderApp(<App />, '/login')

    await signIn()

    expect(await screen.findByRole('alert')).toHaveTextContent('ইমেইল বা পাসওয়ার্ড ভুল।')
  })

  it('FR-06 shows the Bangla 403 page to a worker on /admin, and nothing of the admin page', async () => {
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(tokenResponse('WORKER'))))

    renderApp(<App />, '/admin')

    expect(await screen.findByRole('heading', { name: 'অনুমতি নেই' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('এই পাতাটি দেখার অনুমতি আপনার নেই')
    expect(screen.queryByText('প্রশাসনের পাতা শীঘ্রই আসছে।')).not.toBeInTheDocument()
  })

  it('FR-06 lets an admin open /admin and shows the admin link only to an admin', async () => {
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(tokenResponse('ADMIN'))))

    renderApp(<App />, '/admin')

    expect(await screen.findByText('প্রশাসনের পাতা শীঘ্রই আসছে।')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'প্রশাসন' })).toBeInTheDocument()
  })

  it('FR-06 hides the admin link from a worker', async () => {
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(tokenResponse('WORKER'))))

    renderApp(<App />, '/app')

    await screen.findByRole('heading', { name: 'আপনার চুক্তি দিন' })
    expect(screen.queryByRole('link', { name: 'প্রশাসন' })).not.toBeInTheDocument()
  })

  it('FR-05 logout calls the server with the bearer token, clears state and protects /app again', async () => {
    let authorization: string | null = null
    server.use(
      http.post('/api/v1/auth/refresh', () => HttpResponse.json(tokenResponse('WORKER')), {
        once: true,
      }),
      http.post('/api/v1/auth/logout', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp(<App />, '/app')
    await screen.findByRole('heading', { name: 'আপনার চুক্তি দিন' })

    await userEvent.click(screen.getByRole('button', { name: 'সাইন আউট' }))

    expect(await screen.findByRole('heading', { name: 'সাইন ইন' })).toBeInTheDocument()
    expect(authorization).toBe('Bearer access-token-for-WORKER')
    expect(screen.queryByRole('button', { name: 'সাইন আউট' })).not.toBeInTheDocument()
    expect(
      JSON.stringify([Object.entries(window.localStorage), Object.entries(window.sessionStorage)]),
    ).not.toContain('access-token')
  })

  it('FR-05 logout renews an expired access token once so the refresh cookie is revoked', async () => {
    const calls: string[] = []
    server.use(
      http.post('/api/v1/auth/refresh', () => {
        calls.push('refresh')
        return HttpResponse.json(tokenResponse('WORKER'))
      }),
      http.post('/api/v1/auth/logout', ({ request }) => {
        calls.push(`logout:${request.headers.get('Authorization')}`)
        return calls.filter((c) => c.startsWith('logout')).length === 1
          ? problem(401, 'invalid-token', 'সাইন-ইনের মেয়াদ শেষ')
          : new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp(<App />, '/app')
    await screen.findByRole('heading', { name: 'আপনার চুক্তি দিন' })

    await userEvent.click(screen.getByRole('button', { name: 'সাইন আউট' }))

    await waitFor(() => {
      expect(calls.filter((c) => c.startsWith('logout'))).toHaveLength(2)
    })
    expect(await screen.findByRole('heading', { name: 'সাইন ইন' })).toBeInTheDocument()
  })

  it('FR-05 keeps the access token out of every URL', async () => {
    const urls: string[] = []
    server.events.on('request:start', ({ request }) => {
      urls.push(request.url)
    })
    server.use(http.post('/api/v1/auth/login', () => HttpResponse.json(tokenResponse('WORKER'))))
    renderApp(<App />, '/login')

    await signIn()
    await screen.findByRole('heading', { name: 'আপনার চুক্তি দিন' })

    expect(urls.length).toBeGreaterThan(0)
    for (const url of urls) {
      expect(url).not.toMatch(/token|api_key/i)
    }
    server.events.removeAllListeners()
  })
})
