import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import * as authApi from '../api/auth'
import { ApiError } from '../api/client'
import { AuthContext, type AuthApi, type Session } from './authContext'

/** Renew the access token this long before it expires. */
const REFRESH_MARGIN_MS = 60_000
const MIN_DELAY_MS = 5_000

/**
 * Holds the session in memory only: the access token is never written to storage (AGENTS.md §6),
 * and the refresh token lives in an HttpOnly cookie the page cannot read. On start it tries a
 * silent refresh, and it renews the access token shortly before it expires.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const { i18n } = useTranslation()
  const language = i18n.language === 'en' ? 'en' : 'bn'
  const [session, setSession] = useState<Session>({ status: 'loading' })
  const languageRef = useRef(language)
  useEffect(() => {
    languageRef.current = language
  }, [language])

  const end = useCallback(() => {
    setSession({ status: 'anonymous' })
  }, [])

  /** Starts a session from a token response, or ends it if the response has no user. */
  const start = useCallback(
    (tokens: authApi.TokenResponse) => {
      if (tokens.user === null) {
        end()
        return null
      }
      setSession({
        status: 'authenticated',
        user: tokens.user,
        accessToken: tokens.accessToken,
        expiresInSeconds: tokens.expiresIn,
      })
      return tokens.user
    },
    [end],
  )

  // Silent sign-in from the refresh cookie when the page opens. It only settles a session that is
  // still loading: a login that finished first (for example /demo-user) must not be overwritten
  // when this slower request comes back.
  useEffect(() => {
    let active = true
    authApi.refresh(languageRef.current).then(
      (tokens) => {
        if (active && tokens.user !== null) {
          const { user } = tokens
          setSession((current) =>
            current.status === 'loading'
              ? {
                  status: 'authenticated',
                  user,
                  accessToken: tokens.accessToken,
                  expiresInSeconds: tokens.expiresIn,
                }
              : current,
          )
        }
      },
      () => {
        if (active) {
          setSession((current) =>
            current.status === 'loading' ? { status: 'anonymous' } : current,
          )
        }
      },
    )
    return () => {
      active = false
    }
  }, [])

  // Renew shortly before the access token expires; a failed renewal ends the session.
  useEffect(() => {
    if (session.status !== 'authenticated') {
      return undefined
    }
    const delay = Math.max(MIN_DELAY_MS, session.expiresInSeconds * 1000 - REFRESH_MARGIN_MS)
    const timer = setTimeout(() => {
      authApi.refresh(languageRef.current).then(start, end)
    }, delay)
    return () => {
      clearTimeout(timer)
    }
  }, [session, start, end])

  const api = useMemo<AuthApi>(() => {
    const requireUser = (tokens: authApi.TokenResponse) => {
      const user = start(tokens)
      if (user === null) {
        throw new Error('Expected a user session')
      }
      return user
    }
    return {
      session,
      login: async (email, password) =>
        requireUser(await authApi.login(email, password, languageRef.current)),
      demoLogin: async (role) => requireUser(await authApi.demoLogin(role, languageRef.current)),
      logout: async () => {
        const token = session.status === 'authenticated' ? session.accessToken : null
        try {
          if (token !== null) {
            await authApi.logout(token, languageRef.current)
          }
        } catch (error) {
          // An expired access token: renew it once so the refresh cookie is really revoked.
          if (error instanceof ApiError && error.status === 401) {
            try {
              const renewed = await authApi.refresh(languageRef.current)
              await authApi.logout(renewed.accessToken, languageRef.current)
            } catch {
              // Nothing more can be done from the browser; the local session ends regardless.
            }
          }
        } finally {
          end()
        }
      },
    }
  }, [session, start, end])

  return <AuthContext.Provider value={api}>{children}</AuthContext.Provider>
}
