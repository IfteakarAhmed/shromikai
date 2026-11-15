import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ALLOWED_STORAGE_KEYS,
  deviceToken,
  readPreference,
  resetDeviceTokenForTests,
  writePreference,
} from './storage'

afterEach(() => {
  vi.restoreAllMocks()
  resetDeviceTokenForTests()
  window.localStorage.clear()
})

describe('storage', () => {
  it('R-02 writes only the allowlisted keys: device token and UI preferences', () => {
    deviceToken()
    writePreference('locale', 'en')
    writePreference('side', 'EMPLOYER')
    writePreference('lowBandwidth', true)
    writePreference('theme', 'light')

    expect(Object.keys(window.localStorage).sort()).toEqual([...ALLOWED_STORAGE_KEYS].sort())
    expect(window.sessionStorage.length).toBe(0)
  })

  it('FR-75 reads back what it wrote and falls back to the defaults', () => {
    expect(readPreference('locale')).toBe('bn')
    expect(readPreference('side')).toBe('WORKER')
    expect(readPreference('lowBandwidth')).toBe(false)
    expect(readPreference('theme')).toBe('dark')

    writePreference('locale', 'en')
    writePreference('lowBandwidth', true)
    writePreference('theme', 'light')

    expect(readPreference('locale')).toBe('en')
    expect(readPreference('lowBandwidth')).toBe(true)
    expect(readPreference('theme')).toBe('light')
  })

  it('FR-75 ignores stored values it does not know', () => {
    window.localStorage.setItem('shromikai.side', 'JUDGE')
    window.localStorage.setItem('shromikai.theme', 'purple')
    window.localStorage.setItem('shromikai.locale', 'fr')

    expect(readPreference('side')).toBe('WORKER')
    expect(readPreference('theme')).toBe('dark')
    expect(readPreference('locale')).toBe('bn')
  })

  it('FR-69 keeps one device token, a UUID v4, across calls', () => {
    const first = deviceToken()

    expect(first).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(deviceToken()).toBe(first)
    expect(window.localStorage.getItem('shromikai.device')).toBe(first)
  })

  it('FR-69 replaces a stored device token that is not a UUID v4', () => {
    window.localStorage.setItem('shromikai.device', 'not-a-uuid')

    expect(deviceToken()).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('FR-75 still works when the browser refuses storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })

    expect(readPreference('locale')).toBe('bn')
    expect(() => {
      writePreference('locale', 'en')
    }).not.toThrow()
    const token = deviceToken()
    expect(deviceToken()).toBe(token)
  })
})
