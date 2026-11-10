import { describe, expect, it } from 'vitest'
import { formatNumber, formatVersion } from './format'

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
