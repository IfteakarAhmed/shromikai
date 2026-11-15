/**
 * The only module that touches Web Storage (AGENTS.md §6, R-02). The only values that may be
 * persisted are the guest device token and four UI preferences; analysis results, contract text and
 * memo content never go to localStorage, sessionStorage or IndexedDB. An ESLint rule keeps every
 * other file away from the storage globals.
 */

export type Side = 'WORKER' | 'EMPLOYER'
export type Theme = 'dark' | 'light'
export type Locale = 'bn' | 'en'

interface Preferences {
  locale: Locale
  side: Side
  lowBandwidth: boolean
  theme: Theme
}

const KEYS = {
  locale: 'shromikai.locale',
  side: 'shromikai.side',
  lowBandwidth: 'shromikai.lowbw',
  theme: 'shromikai.theme',
  device: 'shromikai.device',
} as const

/** Every key this app may ever write. A test fails if anything else appears. */
export const ALLOWED_STORAGE_KEYS: readonly string[] = Object.values(KEYS)

const DEFAULTS: Preferences = { locale: 'bn', side: 'WORKER', lowBandwidth: false, theme: 'dark' }

function read(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // Storage may be blocked (private mode, site data off); the choice then lasts for this page.
  }
}

/** A stored preference, or its default when absent, unknown or unreadable. */
export function readPreference<K extends keyof Preferences>(name: K): Preferences[K]
export function readPreference(name: keyof Preferences): Preferences[keyof Preferences] {
  const raw = read(KEYS[name])
  switch (name) {
    case 'locale':
      return raw === 'en' || raw === 'bn' ? raw : DEFAULTS.locale
    case 'side':
      return raw === 'WORKER' || raw === 'EMPLOYER' ? raw : DEFAULTS.side
    case 'theme':
      return raw === 'light' || raw === 'dark' ? raw : DEFAULTS.theme
    case 'lowBandwidth':
      return raw === 'true'
  }
}

export function writePreference<K extends keyof Preferences>(name: K, value: Preferences[K]): void {
  write(KEYS[name], String(value))
}

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

let memoryToken: string | null = null

/**
 * The guest device token (api.md, GuestRequest): a random UUID v4 generated here and kept in the
 * browser, so a guest keeps the same subject across page loads. It identifies a browser, not a
 * person, and carries no contract data.
 */
export function deviceToken(): string {
  const stored = read(KEYS.device)
  if (stored !== null && UUID_V4.test(stored)) {
    memoryToken = stored
    return stored
  }
  if (memoryToken !== null) {
    return memoryToken
  }
  memoryToken = crypto.randomUUID()
  write(KEYS.device, memoryToken)
  return memoryToken
}

/** Test helper: forget the in-memory copy of the device token. */
export function resetDeviceTokenForTests(): void {
  memoryToken = null
}
