import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { sampleUser, tokenResponse } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import App from '../../App'

function signedIn(profile: unknown = null) {
  server.use(
    http.post('/api/v1/auth/refresh', () => HttpResponse.json(tokenResponse('WORKER'))),
    http.get('/api/v1/auth/me', () => HttpResponse.json({ ...sampleUser('WORKER'), profile })),
  )
}

describe('ProfilePage', () => {
  it('FR-56 saves the chosen profile with PATCH /auth/me and says it was saved', async () => {
    signedIn()
    let body: unknown = null
    server.use(
      http.patch('/api/v1/auth/me', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          ...sampleUser('WORKER'),
          profile: (body as { profile: unknown }).profile,
        })
      }),
    )
    renderApp(<App />, '/app/profile')

    await userEvent.selectOptions(await screen.findByLabelText('কাজের খাত'), 'SECURITY_SERVICES')
    await userEvent.type(screen.getByLabelText('পদ বা কাজ'), 'গার্ড')
    await userEvent.selectOptions(screen.getByLabelText('ব্যাখ্যার ধরন'), 'BASIC')
    await userEvent.click(screen.getByRole('button', { name: 'সংরক্ষণ করুন' }))

    expect(await screen.findByText('প্রোফাইল সংরক্ষিত হয়েছে।')).toBeInTheDocument()
    expect(body).toEqual({
      profile: { sector: 'SECURITY_SERVICES', jobRole: 'গার্ড', readingLevel: 'BASIC' },
      locale: null,
    })
  })

  it('FR-56 shows the saved profile and leaves out the fields left empty', async () => {
    signedIn({ sector: 'RETAIL', preferredLanguage: 'en' })
    renderApp(<App />, '/app/profile')

    expect(await screen.findByLabelText('কাজের খাত')).toHaveValue('RETAIL')
    expect(screen.getByLabelText('ব্যাখ্যার ভাষা')).toHaveValue('en')
    expect(screen.getByLabelText('পদ বা কাজ')).toHaveValue('')
  })

  it('FR-56 a guest is told a profile needs an account', async () => {
    server.use(
      http.post('/api/v1/auth/guest', () =>
        HttpResponse.json({
          accessToken: 'g',
          expiresIn: 7200,
          tokenType: 'Bearer',
          guest: true,
          user: null,
        }),
      ),
    )
    renderApp(<App />, '/app/profile')

    expect(await screen.findByText(/অ্যাকাউন্ট লাগে/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'সাইন ইন করুন' })).toBeInTheDocument()
  })

  it('FR-56 shows the profile link only to a signed-in user', async () => {
    signedIn()
    renderApp(<App />, '/app/profile')

    expect(await screen.findByRole('link', { name: 'প্রোফাইল' })).toBeInTheDocument()
  })

  it('FR-68 the fields sit in a two-column grid and use the shared field class', async () => {
    signedIn()
    renderApp(<App />, '/app/profile')

    const grid = await screen.findByTestId('profile-grid')
    expect(grid.className).toMatch(/\bmd:grid-cols-2\b/)
    for (const label of ['কাজের খাত', 'পদ বা কাজ', 'ব্যাখ্যার ভাষা', 'ব্যাখ্যার ধরন']) {
      expect(screen.getByLabelText(label)).toHaveClass('field')
    }
  })

  it('FR-56 FR-68 a side card says what the profile changes and what it never changes', async () => {
    signedIn()
    renderApp(<App />, '/app/profile')

    const note = await screen.findByTestId('profile-effect')
    expect(note).toHaveTextContent('যা বদলায়')
    expect(note).toHaveTextContent('যা কখনো বদলায় না')
    expect(note).toHaveTextContent('কোন ঝুঁকি পাওয়া যাবে')
    expect(note.closest('.depth-card')).not.toBeNull()
  })
})
