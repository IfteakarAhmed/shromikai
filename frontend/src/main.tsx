import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './fonts'
import './index.css'
import i18n from './i18n'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthProvider'
import { readPreference } from './lib/storage'
import { PreferencesProvider } from './state/PreferencesProvider'

document.documentElement.lang = i18n.language
// The theme and low-bandwidth choices are on the page before the first paint.
document.documentElement.dataset['theme'] = readPreference('theme')
document.documentElement.dataset['lowbw'] = String(readPreference('lowBandwidth'))

// Server state lives only in this in-memory cache: no persister (AGENTS.md §6).
const queryClient = new QueryClient({
  defaultOptions: { queries: { gcTime: 30 * 60 * 1000, refetchOnWindowFocus: false } },
})

const root = document.getElementById('root')
if (!root) {
  throw new Error('Missing #root element')
}

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <PreferencesProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </PreferencesProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
