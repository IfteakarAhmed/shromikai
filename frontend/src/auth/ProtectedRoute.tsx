import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation } from 'react-router-dom'
import { isRole, type Role } from '../api/auth'
import { ForbiddenPage } from '../pages/ForbiddenPage'
import { useAuth } from './authContext'

/**
 * Shows its children to a signed-in user, optionally limited to some roles. A visitor without a
 * session is sent to the login page and returns here afterwards; a user with the wrong role sees
 * the Bangla 403 page (FR-06). The server enforces the same rules; this only saves the round trip.
 */
export function ProtectedRoute({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { session } = useAuth()
  const { t } = useTranslation()
  const location = useLocation()

  if (session.status === 'loading') {
    return (
      <p role="status" className="mt-8 text-stone-600">
        {t('common.loading')}
      </p>
    )
  }
  if (session.status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  const role = session.user.role
  if (roles !== undefined && !(isRole(role) && roles.includes(role))) {
    return <ForbiddenPage />
  }
  return <>{children}</>
}
