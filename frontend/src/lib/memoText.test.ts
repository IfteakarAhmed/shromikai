import { describe, expect, it } from 'vitest'
import { makeResult } from '../test/fixtures'
import { memoText } from './memoText'

describe('memoText', () => {
  it('FR-71 writes the title, every section heading and the disclaimer', () => {
    const text = memoText(makeResult())

    expect(text.startsWith('Contract review memo')).toBe(true)
    for (const heading of ['Summary', 'Risks', 'Revised clauses', 'Obligations', 'Next steps']) {
      expect(text).toContain(heading)
    }
    expect(text.trim().endsWith('Talk to a lawyer before an important decision.')).toBe(true)
  })

  it('FR-71 resolves the risks, the proposed text and the duties the memo points at', () => {
    const text = memoText(makeResult())

    expect(text).toContain('- Dismissal without notice (HIGH)')
    expect(text).toContain('The company may end this contract after fourteen days written notice.')
    expect(text).toContain('- Pay the monthly salary stated in the contract')
  })
})
