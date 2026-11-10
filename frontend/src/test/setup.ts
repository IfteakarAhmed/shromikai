import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import i18n from '../i18n'
import { server } from './msw/server'

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
})
afterEach(async () => {
  cleanup()
  server.resetHandlers()
  window.localStorage.clear()
  await i18n.changeLanguage('bn')
})
afterAll(() => {
  server.close()
})
