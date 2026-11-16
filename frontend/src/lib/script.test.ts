import { describe, expect, it } from 'vitest'
import { differentLanguages, scriptOf } from './script'

describe('scriptOf', () => {
  it('FR-71 tells Bangla from English by letters, ignoring digits and marks', () => {
    expect(scriptOf('The company may end this contract.')).toBe('en')
    expect(scriptOf('২. বেতন: মাসিক বেতন 11,000 টাকা।')).toBe('bn')
    expect(scriptOf('12345 ... !!')).toBeNull()
  })

  it('FR-71 a Bangla clause with a few Latin words is still Bangla', () => {
    expect(scriptOf('এই চুক্তি ZQXJ-CANARY অনুযায়ী মাসিক বেতন প্রদান করা হবে।')).toBe('bn')
  })
})

describe('differentLanguages', () => {
  it('FR-71 is true only when both texts have letters and they differ', () => {
    expect(differentLanguages('Salary is paid monthly.', 'বেতন মাসিক দেওয়া হবে।')).toBe(true)
    expect(differentLanguages('Salary is paid.', 'Salary is paid monthly.')).toBe(false)
    expect(differentLanguages(null, 'বেতন')).toBe(false)
    expect(differentLanguages('123', 'বেতন')).toBe(false)
  })
})
