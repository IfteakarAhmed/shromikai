import { describe, expect, it } from 'vitest'
import { clampProgress, stepStates, UI_STAGES } from './stages'

describe('stepStates', () => {
  const text = { ocrExpected: false, finished: false }

  it('FR-58 starts with reading active and everything else waiting', () => {
    expect(stepStates([], text)).toEqual([
      'active',
      'waiting',
      'waiting',
      'waiting',
      'waiting',
      'waiting',
    ])
  })

  it('FR-58 finishes reading on INTAKE for text, which never reports OCR', () => {
    expect(stepStates(['INTAKE'], text).slice(0, 2)).toEqual(['done', 'active'])
  })

  it('FR-58 waits for OCR before finishing reading when a scan is expected', () => {
    const scan = { ocrExpected: true, finished: false }

    expect(stepStates(['INTAKE'], scan)[0]).toBe('active')
    expect(stepStates(['INTAKE', 'OCR'], scan).slice(0, 2)).toEqual(['done', 'active'])
  })

  it('FR-58 marks a step done only when the last stage of its group is reported', () => {
    const states = stepStates(['INTAKE', 'REDACT', 'CLASSIFY', 'RETRIEVE'], text)

    expect(states).toEqual(['done', 'done', 'done', 'active', 'waiting', 'waiting'])
    expect(stepStates(['INTAKE', 'REDACT', 'CLASSIFY', 'RETRIEVE', 'RULES'], text)[3]).toBe('done')
  })

  it('FR-58 treats a skipped stage as passed when a later one is reported', () => {
    expect(stepStates(['RISK'], text)).toEqual(['done', 'done', 'done', 'done', 'done', 'active'])
  })

  it('FR-58 shows every step done when the analysis is finished', () => {
    expect(stepStates([], { ocrExpected: false, finished: true })).toEqual(
      UI_STAGES.map(() => 'done'),
    )
  })
})

describe('clampProgress', () => {
  it('FR-58 keeps the percentage within 0 to 100', () => {
    expect(clampProgress(-3)).toBe(0)
    expect(clampProgress(41.6)).toBe(42)
    expect(clampProgress(250)).toBe(100)
  })
})
