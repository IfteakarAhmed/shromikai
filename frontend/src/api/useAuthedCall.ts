import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/authContext'
import { ApiError } from './client'

/**
 * Runs an API call with the current access token and the page language. A `401` renews the token
 * once (user: refresh cookie; guest: device token) and repeats the call, so a long visit does not
 * end in a login screen.
 */
export function useAuthedCall() {
  const { getToken, renewToken } = useAuth()
  const { i18n } = useTranslation()
  const language = i18n.language === 'en' ? 'en' : 'bn'
  return useCallback(
    async <T>(call: (token: string | null, language: string) => Promise<T>): Promise<T> => {
      try {
        return await call(getToken(), language)
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          const renewed = await renewToken()
          if (renewed !== null) {
            return call(renewed, language)
          }
        }
        throw error
      }
    },
    [getToken, renewToken, language],
  )
}
