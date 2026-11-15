import { createContext, useContext } from 'react'
import type { DemoRole, Me } from '../api/auth'

export type Session =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: Me; accessToken: string; expiresInSeconds: number }
  | { status: 'guest'; accessToken: string; expiresInSeconds: number }

export interface AuthApi {
  session: Session
  login: (email: string, password: string) => Promise<Me>
  demoLogin: (role: DemoRole) => Promise<Me>
  /** Starts a guest session bound to this browser's device token (FR-09). */
  guestLogin: () => Promise<void>
  /** The access token of the session right now, or null. Read it when making a request. */
  getToken: () => string | null
  /** A fresh access token (user: refresh cookie; guest: the device token), or null. */
  renewToken: () => Promise<string | null>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthApi | null>(null)

export function useAuth(): AuthApi {
  const value = useContext(AuthContext)
  if (value === null) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return value
}
