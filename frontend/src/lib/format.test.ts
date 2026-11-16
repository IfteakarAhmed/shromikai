import { describe, expect, it } from 'vitest'
import { formatDateTime, formatNumber, formatRatio, formatVersion } from './format'

describe('format', () => {
  it('NFR-09 formats counts with Bangla numerals for bn', () => {
    expect(formatNumber(112, 'bn')).toBe('১১২')
    expect(formatNumber(112, 'en')).toBe('112')
  })

  it('NFR-09 shows the build version in Bangla digits for bn and unchanged for en', () => {
    expect(formatVersion('0.1.0', 'bn')).toBe('০.১.০')
    expect(formatVersion('1.12.3-rc1', 'bn')).toBe('১.১২.৩-rc১')
    expect(formatVersion('0.1.0', 'en')).toBe('0.1.0')
  })
})

describe('admin formats (screenshot review)', () => {
  it('FR-75 a Bangla date and time has no Latin letters, so no AM or PM', () => {
    expect(formatDateTime('2026-11-15T15:56:00Z', 'bn')).not.toMatch(/[A-Za-z]/)
    expect(formatDateTime('2026-11-15T15:56:00Z', 'en')).not.toMatch(/[AP]M/i)
  })

  it('FR-75 a small ratio is not rounded to zero', () => {
    expect(formatRatio(1 / 296, 'en')).toBe('0.003')
    expect(formatRatio(1.5, 'en')).toBe('1.5')
    expect(formatRatio(1 / 296, 'bn')).toMatch(/^০\.০০৩$/)
  })
})
