import { Suspense, lazy, type ReactElement } from 'react'
import { Outlet, Route, Routes } from 'react-router-dom'
import { isStaticReplay } from './api/replay'
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
import { ReplayUnavailablePage } from './pages/ReplayUnavailablePage'

// Areas most visitors never open are separate chunks (architecture §17: route-level splitting).
const AdminLayout = lazy(() => import('./features/admin/AdminLayout'))
const AdminHome = lazy(() => import('./features/admin/AdminHome'))
const ReviewPage = lazy(() => import('./features/admin/ReviewPage'))
const PlaybooksPage = lazy(() => import('./features/admin/PlaybooksPage'))
const ApiKeysPage = lazy(() => import('./features/admin/ApiKeysPage'))
const UsagePage = lazy(() => import('./features/admin/UsagePage'))
const TracePage = lazy(() => import('./features/admin/TracePage'))
const DocsLayout = lazy(() => import('./features/docs/DocsLayout'))
const DocsIndex = lazy(() => import('./features/docs/DocsIndex'))
const ArchitecturePage = lazy(() => import('./features/docs/ArchitecturePage'))
const DataPage = lazy(() => import('./features/docs/DataPage'))
const SecurityPage = lazy(() => import('./features/docs/SecurityPage'))
const AuditPage = lazy(() => import('./features/docs/AuditPage'))
const EvaluationPage = lazy(() => import('./features/docs/EvaluationPage'))
const PromptsPage = lazy(() => import('./features/docs/PromptsPage'))
const McpPage = lazy(() => import('./features/docs/McpPage'))
const KnownGapsPage = lazy(() => import('./features/docs/KnownGapsPage'))

function RouteFallback() {
  return (
    <div className="mx-auto max-w-6xl space-y-4 px-(--gutter) py-10" aria-hidden="true">
      <Skeleton className="h-9 w-1/3" />
      <Skeleton className="h-64 w-full" />
    </div>
  )
}

export default function App() {
  // The static recorded replay has no server: what needs one says so (FR-85, ADR-0027).
  const replay = isStaticReplay()
  const live = (element: ReactElement): ReactElement =>
    replay ? <ReplayUnavailablePage /> : element
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<LandingPage />} />
        <Route path="login" element={live(<LoginPage />)} />
        <Route path="demo-user" element={<DemoLoginPage role="worker" />} />
        <Route path="demo-hr" element={live(<DemoLoginPage role="hr" />)} />
        <Route path="demo-admin" element={live(<DemoLoginPage role="admin" />)} />
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
          <Route path="profile" element={live(<ProfilePage />)} />
          <Route path="tickets/:ticketId" element={live(<TicketPage />)} />
          <Route path="compare" element={live(<CompareStartPage />)} />
          <Route path="compare/:id" element={live(<CompareResultPage />)} />
        </Route>
        <Route
          path="admin"
          element={live(
            <ProtectedRoute roles={['ADMIN']}>
              <Suspense fallback={<RouteFallback />}>
                <AdminLayout />
              </Suspense>
            </ProtectedRoute>,
          )}
        >
          <Route index element={<AdminHome />} />
          <Route path="review/:id?" element={<ReviewPage />} />
          <Route path="playbooks" element={<PlaybooksPage />} />
          <Route path="api-keys" element={<ApiKeysPage />} />
          <Route path="usage" element={<UsagePage />} />
          <Route path="traces/:analysisId?" element={<TracePage />} />
        </Route>
        <Route
          path="docs"
          element={
            <Suspense fallback={<RouteFallback />}>
              <DocsLayout />
            </Suspense>
          }
        >
          <Route index element={<DocsIndex />} />
          <Route path="architecture" element={<ArchitecturePage />} />
          <Route path="data" element={<DataPage />} />
          <Route path="security" element={<SecurityPage />} />
          <Route path="audit" element={<AuditPage />} />
          <Route path="evaluation" element={<EvaluationPage />} />
          <Route path="prompts/:id?" element={<PromptsPage />} />
          <Route path="mcp" element={<McpPage />} />
          <Route path="known-gaps" element={<KnownGapsPage />} />
        </Route>
        <Route
          path="app/playbooks"
          element={live(
            <ProtectedRoute roles={['HR', 'ADMIN']}>
              <div className="mx-auto max-w-6xl px-(--gutter) py-8">
                <Suspense fallback={<RouteFallback />}>
                  <PlaybooksPage />
                </Suspense>
              </div>
            </ProtectedRoute>,
          )}
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
