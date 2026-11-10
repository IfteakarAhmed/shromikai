import { useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError, NetworkError } from '../api/client'
import { formatNumber } from '../lib/format'

/** Same shape as the server's check; the server stays the authority. */
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

type Errors = { email?: string; password?: string }

interface Props {
  onSubmit: (email: string, password: string) => Promise<unknown>
}

const inputClass =
  'mt-1 block min-h-11 w-full rounded-md border border-stone-400 bg-white px-3 py-2 text-base focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700'

export function LoginForm({ onSubmit }: Props) {
  const { t, i18n } = useTranslation()
  const language = i18n.language === 'en' ? 'en' : 'bn'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function validate(): Errors {
    const found: Errors = {}
    const trimmed = email.trim()
    if (trimmed === '') {
      found.email = t('login.errors.emailRequired')
    } else if (!EMAIL.test(trimmed)) {
      found.email = t('login.errors.emailInvalid')
    }
    if (password === '') {
      found.password = t('login.errors.passwordRequired')
    }
    return found
  }

  function describe(error: unknown): string {
    if (error instanceof NetworkError) {
      return t('login.errors.network')
    }
    if (error instanceof ApiError) {
      switch (error.problem?.code) {
        case 'invalid-credentials':
          return t('login.errors.invalid')
        case 'account-locked': {
          const seconds = (error.problem as { retryAfterSeconds?: number }).retryAfterSeconds ?? 900
          return t('login.errors.locked', {
            minutes: formatNumber(Math.max(1, Math.ceil(seconds / 60)), language),
          })
        }
        case 'rate-limited':
          return t('login.errors.rateLimited')
        case 'validation-failed':
          return t('login.errors.emailInvalid')
      }
    }
    return t('login.errors.generic')
  }

  async function handleSubmit(event: SyntheticEvent) {
    event.preventDefault()
    const found = validate()
    setErrors(found)
    setFailure(null)
    if (found.email !== undefined || found.password !== undefined) {
      return
    }
    setBusy(true)
    try {
      await onSubmit(email.trim(), password)
    } catch (error) {
      setFailure(describe(error))
      setPassword('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="mt-6 space-y-5">
      <div>
        <label htmlFor="login-email" className="font-medium">
          {t('login.email')}
        </label>
        <input
          id="login-email"
          type="email"
          autoComplete="username"
          inputMode="email"
          value={email}
          onChange={(event) => {
            setEmail(event.target.value)
          }}
          aria-invalid={errors.email !== undefined}
          aria-describedby={errors.email === undefined ? undefined : 'login-email-error'}
          className={inputClass}
        />
        {errors.email !== undefined && (
          <p id="login-email-error" className="mt-1 text-red-800">
            {errors.email}
          </p>
        )}
      </div>
      <div>
        <label htmlFor="login-password" className="font-medium">
          {t('login.password')}
        </label>
        <input
          id="login-password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => {
            setPassword(event.target.value)
          }}
          aria-invalid={errors.password !== undefined}
          aria-describedby={errors.password === undefined ? undefined : 'login-password-error'}
          className={inputClass}
        />
        {errors.password !== undefined && (
          <p id="login-password-error" className="mt-1 text-red-800">
            {errors.password}
          </p>
        )}
      </div>
      {failure !== null && (
        <p role="alert" className="text-red-800">
          {failure}
        </p>
      )}
      <button
        type="submit"
        disabled={busy}
        className="min-h-11 w-full rounded-md bg-emerald-800 px-4 py-2 font-medium text-white hover:bg-emerald-900 disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
      >
        {busy ? t('login.submitting') : t('login.submit')}
      </button>
    </form>
  )
}
