import { createContext, useContext } from 'react'
import type { DemoRole, Me } from '../api/auth'

export type Session =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: Me; accessToken: string; expiresInSeconds: number }

export interface AuthApi {
  session: Session
  login: (email: string, password: string) => Promise<Me>
  demoLogin: (role: DemoRole) => Promise<Me>
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
