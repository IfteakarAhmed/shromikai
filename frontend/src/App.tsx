import { Suspense, lazy } from 'react'
import { Outlet, Route, Routes } from 'react-router-dom'
import { AppAccess } from './auth/AppAccess'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Skeleton } from './components/ui/Skeleton'
import { AnalysisPage } from './features/analysis/AnalysisPage'
import { LandingPage } from './features/landing/LandingPage'
import { UploadPage } from './features/upload/UploadPage'
import { DemoLoginPage } from './pages/DemoLoginPage'
import { Layout } from './pages/Layout'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'

// Areas most visitors never open are separate chunks (architecture §17: route-level splitting).
const AdminHome = lazy(() => import('./features/admin/AdminHome'))
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
        </Route>
        <Route
          path="admin/*"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <Suspense fallback={<RouteFallback />}>
                <AdminHome />
              </Suspense>
            </ProtectedRoute>
          }
        />
        <Route
          path="docs/*"
          element={
            <Suspense fallback={<RouteFallback />}>
              <DocsHub />
            </Suspense>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
