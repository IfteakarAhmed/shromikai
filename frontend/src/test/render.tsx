import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthProvider'
import { PreferencesProvider } from '../state/PreferencesProvider'

export function renderWithClient(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <PreferencesProvider>{ui}</PreferencesProvider>
    </QueryClientProvider>,
  )
}

/** The whole app shell (router, session, query cache) starting at {@code route}. */
export function renderApp(
  ui: ReactElement,
  route: string | { pathname: string; state?: unknown } = '/',
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <PreferencesProvider>
          <AuthProvider>{ui}</AuthProvider>
        </PreferencesProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}
