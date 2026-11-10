import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import App from '../App'
import { problem, tokenResponse } from '../test/msw/handlers'
import { server } from '../test/msw/server'
import { renderApp } from '../test/render'

describe('demo login', () => {
  it('FR-08 /demo-user signs in as the demo worker with one click and lands on /app', async () => {
    let called = 0
    server.use(
      http.post('/api/v1/auth/demo/worker', () => {
        called += 1
        return HttpResponse.json(tokenResponse('WORKER', true))
      }),
    )

    renderApp(<App />, '/demo-user')

    expect(await screen.findByText('বিশ্লেষণের পাতা শীঘ্রই আসছে।')).toBeInTheDocument()
    expect(called).toBe(1)
  })

  it('FR-08 /demo-hr lands on /app as HR', async () => {
    server.use(
      http.post('/api/v1/auth/demo/hr', () => HttpResponse.json(tokenResponse('HR', true))),
    )

    renderApp(<App />, '/demo-hr')

    expect(await screen.findByText('বিশ্লেষণের পাতা শীঘ্রই আসছে।')).toBeInTheDocument()
    expect(screen.getByText(/এইচআর/)).toBeInTheDocument()
  })

  it('FR-08 /demo-admin lands on /admin', async () => {
    server.use(
      http.post('/api/v1/auth/demo/admin', () => HttpResponse.json(tokenResponse('ADMIN', true))),
    )

    renderApp(<App />, '/demo-admin')

    expect(await screen.findByText('প্রশাসনের পাতা শীঘ্রই আসছে।')).toBeInTheDocument()
  })

  it('FR-08 shows a Bangla message and a way out when demo sign-in is not available', async () => {
    server.use(
      http.post('/api/v1/auth/demo/worker', () => problem(404, 'not-found', 'পাওয়া যায়নি')),
    )

    renderApp(<App />, '/demo-user')

    expect(await screen.findByRole('alert')).toHaveTextContent('ডেমো সাইন ইন এখন চালু নেই')
    expect(screen.getByRole('link', { name: 'সাধারণ সাইন ইনে যান' })).toHaveAttribute(
      'href',
      '/login',
    )
  })
})
