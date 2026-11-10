import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError, NetworkError, type Problem } from '../api/client'
import { LoginForm } from './LoginForm'

function problem(code: string, extra: Record<string, unknown> = {}): Problem {
  return {
    type: `urn:shromikai:problem:${code}`,
    title: 'x',
    status: 400,
    instance: '/x',
    code,
    traceId: 'r1',
    ...extra,
  }
}

async function fill(email: string, password: string) {
  if (email !== '') await userEvent.type(screen.getByLabelText('ইমেইল'), email)
  if (password !== '') await userEvent.type(screen.getByLabelText('পাসওয়ার্ড'), password)
  await userEvent.click(screen.getByRole('button', { name: 'সাইন ইন করুন' }))
}

describe('LoginForm', () => {
  it('FR-05 asks for both fields in Bangla and does not submit an empty form', async () => {
    const onSubmit = vi.fn()
    render(<LoginForm onSubmit={onSubmit} />)

    await fill('', '')

    expect(screen.getByText('ইমেইল লিখুন।')).toBeInTheDocument()
    expect(screen.getByText('পাসওয়ার্ড লিখুন।')).toBeInTheDocument()
    expect(screen.getByLabelText('ইমেইল')).toHaveAttribute('aria-invalid', 'true')
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('FR-05 rejects an email without a domain', async () => {
    const onSubmit = vi.fn()
    render(<LoginForm onSubmit={onSubmit} />)

    await fill('worker@', 'secret-secret-1')

    expect(screen.getByText('সঠিক ইমেইল লিখুন।')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('FR-05 submits the trimmed email and the password', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<LoginForm onSubmit={onSubmit} />)

    await fill('  worker@example.test ', 'secret-secret-1')

    expect(onSubmit).toHaveBeenCalledWith('worker@example.test', 'secret-secret-1')
  })

  it('FR-05 shows the wrong-password message and clears the password field', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(401, problem('invalid-credentials')))
    render(<LoginForm onSubmit={onSubmit} />)

    await fill('worker@example.test', 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('ইমেইল বা পাসওয়ার্ড ভুল।')
    expect(screen.getByLabelText('পাসওয়ার্ড')).toHaveValue('')
  })

  it('FR-07 tells how many minutes the lock lasts, in Bangla digits', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(423, problem('account-locked', { retryAfterSeconds: 840 })))
    render(<LoginForm onSubmit={onSubmit} />)

    await fill('worker@example.test', 'whatever-123')

    expect(await screen.findByRole('alert')).toHaveTextContent('১৪ মিনিট পরে')
  })

  it('FR-07 shows the rate-limit message', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(429, problem('rate-limited')))
    render(<LoginForm onSubmit={onSubmit} />)

    await fill('worker@example.test', 'whatever-123')

    expect(await screen.findByRole('alert')).toHaveTextContent('অনেক বেশি চেষ্টা হয়েছে')
  })

  it('FR-05 shows a network message when the server cannot be reached', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new NetworkError())
    render(<LoginForm onSubmit={onSubmit} />)

    await fill('worker@example.test', 'whatever-123')

    expect(await screen.findByRole('alert')).toHaveTextContent('সার্ভারের সাথে যোগাযোগ হচ্ছে না')
  })
})
