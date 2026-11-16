import { Suspense, lazy } from 'react'
import { Outlet, Route, Routes } from 'react-router-dom'
import { AppAccess } from './auth/AppAccess'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Skeleton } from './components/ui/Skeleton'
import { AnalysisPage } from './features/analysis/AnalysisPage'
import { CompareResultPage, CompareStartPage } from './features/compare/ComparePage'
import { ProfilePage } from './features/profile/ProfilePage'
import { TicketPage } from './features/ticket/TicketPage'
import { LandingPage } from './features/landing/LandingPage'
import { UploadPage } from './features/upload/UploadPage'
import { DemoLoginPage } from './pages/DemoLoginPage'
import { Layout } from './pages/Layout'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'

// Areas most visitors never open are separate chunks (architecture §17: route-level splitting).
const AdminLayout = lazy(() => import('./features/admin/AdminLayout'))
const AdminHome = lazy(() => import('./features/admin/AdminHome'))
const ReviewPage = lazy(() => import('./features/admin/ReviewPage'))
const PlaybooksPage = lazy(() => import('./features/admin/PlaybooksPage'))
const ApiKeysPage = lazy(() => import('./features/admin/ApiKeysPage'))
const UsagePage = lazy(() => import('./features/admin/UsagePage'))
const TracePage = lazy(() => import('./features/admin/TracePage'))
const DocsHub = lazy(() => import('./features/docs/DocsHub'))

function RouteFallback() {
  return (
    <div className="mx-auto max-w-6xl space-y-4 px-(--gutter) py-10" aria-hidden="true">
      <Skeleton className="h-9 w-1/3" />
      <Skeleton className="h-64 w-full" />
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<LandingPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="demo-user" element={<DemoLoginPage role="worker" />} />
        <Route path="demo-hr" element={<DemoLoginPage role="hr" />} />
        <Route path="demo-admin" element={<DemoLoginPage role="admin" />} />
        <Route
          path="app"
          element={
            <AppAccess>
              <Outlet />
            </AppAccess>
          }
        >
          <Route index element={<UploadPage />} />
          <Route path="analyses/:id" element={<AnalysisPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="tickets/:ticketId" element={<TicketPage />} />
          <Route path="compare" element={<CompareStartPage />} />
          <Route path="compare/:id" element={<CompareResultPage />} />
        </Route>
        <Route
          path="admin"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <Suspense fallback={<RouteFallback />}>
                <AdminLayout />
              </Suspense>
            </ProtectedRoute>
          }
        >
          <Route index element={<AdminHome />} />
          <Route path="review/:id?" element={<ReviewPage />} />
          <Route path="playbooks" element={<PlaybooksPage />} />
          <Route path="api-keys" element={<ApiKeysPage />} />
          <Route path="usage" element={<UsagePage />} />
          <Route path="traces/:analysisId?" element={<TracePage />} />
        </Route>
        <Route
          path="docs/*"
          element={
            <Suspense fallback={<RouteFallback />}>
              <DocsHub />
            </Suspense>
          }
        />
        <Route
          path="app/playbooks"
          element={
            <ProtectedRoute roles={['HR', 'ADMIN']}>
              <div className="mx-auto max-w-6xl px-(--gutter) py-8">
                <Suspense fallback={<RouteFallback />}>
                  <PlaybooksPage />
                </Suspense>
              </div>
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
