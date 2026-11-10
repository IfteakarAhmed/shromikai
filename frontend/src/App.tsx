import { Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { DemoLoginPage } from './pages/DemoLoginPage'
import { Layout } from './pages/Layout'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { AdminHomePage, AppHomePage } from './pages/PlaceholderPages'
import { StatusPage } from './pages/StatusPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<StatusPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="demo-user" element={<DemoLoginPage role="worker" />} />
        <Route path="demo-hr" element={<DemoLoginPage role="hr" />} />
        <Route path="demo-admin" element={<DemoLoginPage role="admin" />} />
        <Route
          path="app"
          element={
            <ProtectedRoute>
              <AppHomePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminHomePage />
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
