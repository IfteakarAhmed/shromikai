import { describe, expect, it } from 'vitest'
import { withoutRepeatedSections, type Block } from './memoBlocks'

describe('withoutRepeatedSections', () => {
  it('FR-71 lists a section once per finding and again for the next finding', () => {
    const keys = new Map([
      ['a', 'X:26@o'],
      ['b', 'X:26@o'],
      ['c', 'X:5@o'],
    ])
    const blocks: Block[] = [
      { type: 'findingRef', findingId: 'f1' },
      { type: 'citationChip', citationId: 'a' },
      { type: 'citationChip', citationId: 'b' },
      { type: 'citationChip', citationId: 'c' },
      { type: 'findingRef', findingId: 'f2' },
      { type: 'citationChip', citationId: 'b' },
    ]

    expect(
      withoutRepeatedSections(blocks, keys).map((b) =>
        b.type === 'citationChip' ? b.citationId : b.type,
      ),
    ).toEqual(['findingRef', 'a', 'c', 'findingRef', 'b'])
  })
})
