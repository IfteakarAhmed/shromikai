import { describe, expect, it } from 'vitest'
import { makeCitation } from '../test/fixtures'
import { groupCitations } from './citations'

describe('groupCitations', () => {
  it('FR-70 shows one chip for two passages of the same section and keeps both quotes', () => {
    const groups = groupCitations([
      makeCitation({ citationId: 'a', quote: 'First passage of the section here.' }),
      makeCitation({ citationId: 'b', quote: 'Second passage of the same section.' }),
    ])

    expect(groups).toHaveLength(1)
    expect(groups[0]?.quotes).toEqual([
      'First passage of the section here.',
      'Second passage of the same section.',
    ])
  })

  it('FR-70 drops an exact duplicate quote', () => {
    const groups = groupCitations([
      makeCitation({ citationId: 'a' }),
      makeCitation({ citationId: 'b' }),
    ])

    expect(groups[0]?.quotes).toHaveLength(1)
  })

  it('FR-70 keeps two versions of a section apart and says which version each is', () => {
    const groups = groupCitations([
      makeCitation({ sectionKey: 'XX-ACT-1:5@original', versionLabel: 'original' }),
      makeCitation({ sectionKey: 'XX-ACT-1:5@amend-1', versionLabel: 'amend-1' }),
      makeCitation({ sectionKey: 'XX-ACT-1:6@original', sectionNo: '6' }),
    ])

    expect(groups.map((g) => g.showVersion)).toEqual([true, true, false])
  })
})
