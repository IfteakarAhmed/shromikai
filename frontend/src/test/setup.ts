import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import i18n from '../i18n'
import { server } from './msw/server'

// jsdom has no modal <dialog> behaviour yet: model showModal and close closely enough for the
// sheet tests (the open attribute, the close event).
if (typeof HTMLDialogElement.prototype.showModal !== 'function') {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (this.hasAttribute('open')) {
      this.removeAttribute('open')
      this.dispatchEvent(new Event('close'))
    }
  }
}

// jsdom has no matchMedia; a test that needs a match replaces it.
if (typeof window.matchMedia !== 'function') {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList
}

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
})
afterEach(async () => {
  cleanup()
  server.resetHandlers()
  window.localStorage.clear()
  delete document.documentElement.dataset['theme']
  delete document.documentElement.dataset['lowbw']
  await i18n.changeLanguage('bn')
})
afterAll(() => {
  server.close()
})
