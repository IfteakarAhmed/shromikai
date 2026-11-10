import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import App from './App'
import { server } from './test/msw/server'
import { renderWithClient } from './test/render'

describe('App', () => {
  it('FR-01 shows the backend status from the meta endpoint', async () => {
    renderWithClient(<App />)

    expect(screen.getByRole('status')).toHaveTextContent('লোড হচ্ছে')
    expect(await screen.findByTestId('backend-status')).toHaveTextContent('চালু (সংস্করণ ০.১.০)')
    expect(screen.getAllByText('১১২টি')).toHaveLength(2)
    expect(screen.getByText('৩টি')).toBeInTheDocument()
    expect(screen.getByText(/এটা আইনি পরামর্শ নয়/)).toBeInTheDocument()
  })

  it('FR-02 shows a Bangla error message when the backend is down', async () => {
    server.use(http.get('/api/v1/meta', () => HttpResponse.error()))

    renderWithClient(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('ব্যাকএন্ডের সাথে যোগাযোগ হচ্ছে না')
  })

  it('FR-02 shows the localised problem title when the backend reports an error', async () => {
    server.use(
      http.get('/api/v1/meta', () =>
        HttpResponse.json(
          {
            type: 'urn:shromikai:problem:internal-error',
            title: 'অপ্রত্যাশিত ত্রুটি',
            status: 500,
            instance: '/api/v1/meta#r1',
            code: 'internal-error',
            traceId: 'r1',
          },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    renderWithClient(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('অপ্রত্যাশিত ত্রুটি')
  })

  it('FR-02 retries after a failure', async () => {
    server.use(http.get('/api/v1/meta', () => HttpResponse.error(), { once: true }))
    renderWithClient(<App />)
    await screen.findByRole('alert')

    await userEvent.click(screen.getByRole('button', { name: 'আবার চেষ্টা করুন' }))

    expect(await screen.findByTestId('backend-status')).toBeInTheDocument()
  })

  it('NFR-09 switches to English and remembers the choice', async () => {
    renderWithClient(<App />)
    await screen.findByTestId('backend-status')

    await userEvent.click(screen.getByRole('button', { name: 'ভাষা পরিবর্তন করে ইংরেজি করুন' }))

    expect(await screen.findByTestId('backend-status')).toHaveTextContent('Running (version 0.1.0)')
    expect(screen.getAllByText('112')).toHaveLength(2)
    expect(window.localStorage.getItem('shromikai.locale')).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })
})
