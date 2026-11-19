import { describe, expect, it } from 'vitest'
import {
  ACCEPTED_FILE,
  LOGIN_FILE,
  analysisFile,
  memoFile,
  metaFile,
  sectionFile,
  traceFile,
} from '../../scripts/replay-files.mjs'

/** The exporter writes these names and the app reads them: one module decides them (FR-85). */
describe('FR-85 static replay file names', () => {
  it('FR-85 names the per-language files after the language', () => {
    expect(metaFile('bn')).toBe('meta.bn.json')
    expect(analysisFile('en')).toBe('analysis.en.json')
    expect(traceFile('bn')).toBe('trace.bn.json')
    expect(memoFile('en')).toBe('memo.en.pdf')
  })

  it('FR-85 has fixed names for the files that do not depend on the language', () => {
    expect(LOGIN_FILE).toBe('login.worker.json')
    expect(ACCEPTED_FILE).toBe('analysis-accepted.json')
  })

  it('FR-85 turns a section key into a safe file name', () => {
    const name = sectionFile('BLA-2006', '100A', '2006-v1', 'bn')
    expect(name).toBe('laws/BLA-2006_100A_2006-v1.bn.json')
    expect(sectionFile('x/../y', '1', 'v:1', 'en')).not.toMatch(/\.\.|:|\/\//)
  })

  it('FR-85 gives two different sections two different names', () => {
    expect(sectionFile('BLA-2006', '3', 'v1', 'bn')).not.toBe(
      sectionFile('BLA-2006', '4', 'v1', 'bn'),
    )
    expect(sectionFile('BLA-2006', '3', 'v1', 'bn')).not.toBe(
      sectionFile('BLA-2006', '3', 'v1', 'en'),
    )
  })
})
