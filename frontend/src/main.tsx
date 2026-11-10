import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './fonts'
import './index.css'
import i18n from './i18n'
import App from './App'

document.documentElement.lang = i18n.language

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
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
