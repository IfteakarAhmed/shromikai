import { describe, expect, it } from 'vitest'
import { makeFinding } from '../test/fixtures'
import { sortFindings } from './findings'

describe('sortFindings', () => {
  it('FR-70 puts the most serious risks first and keeps the API order within a severity', () => {
    const sorted = sortFindings([
      makeFinding({ id: 'f1', severity: 'LOW' }),
      makeFinding({ id: 'f2', severity: 'CRITICAL' }),
      makeFinding({ id: 'f3', severity: 'MEDIUM' }),
      makeFinding({ id: 'f4', severity: 'CRITICAL' }),
      makeFinding({ id: 'f5', severity: 'HIGH' }),
    ])

    expect(sorted.map((f) => f.id)).toEqual(['f2', 'f4', 'f5', 'f3', 'f1'])
  })

  it('FR-70 does not change the list it is given', () => {
    const input = [
      makeFinding({ id: 'f1', severity: 'LOW' }),
      makeFinding({ id: 'f2', severity: 'HIGH' }),
    ]

    sortFindings(input)

    expect(input.map((f) => f.id)).toEqual(['f1', 'f2'])
  })
})
