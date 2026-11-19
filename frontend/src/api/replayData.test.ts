/// <reference types="node" />
import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  ACCEPTED_FILE,
  LOGIN_FILE,
  MANIFEST_FILE,
  analysisFile,
  memoFile,
  metaFile,
  sectionFile,
  traceFile,
} from '../../scripts/replay-files.mjs'
import { assertNoSecrets, collectSectionRefs } from '../../scripts/replay-export-core.mjs'

/**
 * The files of the static recorded replay are real backend responses saved by
 * scripts/export-replay.mjs (FR-85, ADR-0027, R-12): these tests fail when one was edited by hand,
 * is missing, or when a secret or a private-table value got in.
 */
const DIR = join(import.meta.dirname, '..', '..', 'public', 'replay')
const read = (file: string) => readFileSync(join(DIR, file))
const json = (file: string) => JSON.parse(read(file).toString('utf8')) as Record<string, unknown>

function allFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry)
    return statSync(path).isDirectory() ? allFiles(path) : [relative(DIR, path)]
  })
}

interface Manifest {
  analysisId: string
  redactions: { file: string; path: string }[]
  files: { path: string; sha256: string; bytes: number; request: string }[]
}
const manifest = json(MANIFEST_FILE) as unknown as Manifest

describe('FR-85 exported replay files', () => {
  it('FR-85 lists every file in the manifest with its request, size and SHA-256, and nothing else is there', () => {
    const listed = manifest.files.map((f) => f.path).sort()
    expect(
      allFiles(DIR)
        .filter((f) => f !== MANIFEST_FILE)
        .sort(),
    ).toEqual(listed)
    for (const entry of manifest.files) {
      const bytes = read(entry.path)
      expect(bytes.length, entry.path).toBe(entry.bytes)
      expect(createHash('sha256').update(bytes).digest('hex'), entry.path).toBe(entry.sha256)
      expect(entry.request, entry.path).toMatch(/^(GET|POST) \/api\/v1\//)
    }
  })

  it('FR-85 has every file the app reads, in both languages', () => {
    const paths = new Set(manifest.files.map((f) => f.path))
    for (const language of ['bn', 'en'] as const) {
      for (const name of [
        metaFile(language),
        analysisFile(language),
        traceFile(language),
        memoFile(language),
      ]) {
        expect(paths.has(name), name).toBe(true)
      }
    }
    for (const name of [LOGIN_FILE, ACCEPTED_FILE]) {
      expect(paths.has(name), name).toBe(true)
    }
  })

  it('FR-85 has a section file, in both languages, for every section the result cites', () => {
    const refs = collectSectionRefs(json(analysisFile('bn')))
    expect(refs.length).toBeGreaterThan(0)
    for (const ref of refs) {
      for (const language of ['bn', 'en'] as const) {
        const file = sectionFile(ref.actCode, ref.sectionNo, ref.versionLabel, language)
        expect(existsSync(join(DIR, file)), file).toBe(true)
      }
    }
  })

  it('FR-85 is the recorded sample run: one analysis ID everywhere, recorded and sample flags set', () => {
    const accepted = json(ACCEPTED_FILE)
    expect(accepted['analysisId']).toBe(manifest.analysisId)
    for (const language of ['bn', 'en'] as const) {
      const result = json(analysisFile(language)) as {
        analysisId: string
        status: string
        provenance: { recorded: boolean }
        input: { sample: boolean }
        findings: unknown[]
      }
      expect(result.analysisId).toBe(manifest.analysisId)
      expect(result.status).toBe('SUCCEEDED')
      expect(result.provenance.recorded).toBe(true)
      expect(result.input.sample).toBe(true)
      expect(result.findings.length).toBeGreaterThanOrEqual(3)
      expect(json(traceFile(language))['analysisId']).toBe(manifest.analysisId)
    }
  })

  it('FR-85 shows only verified citations on every finding that has one (R-01)', () => {
    const result = json(analysisFile('bn')) as {
      findings: { verificationStatus: string; citations: unknown[] }[]
    }
    for (const finding of result.findings.filter((f) => f.citations.length > 0)) {
      expect(finding.verificationStatus).toBe('VERIFIED')
    }
  })

  it('FR-85 holds no token, no secret field and no private-table value', () => {
    for (const file of allFiles(DIR).filter((f) => /\.json$/.test(f))) {
      assertNoSecrets(read(file).toString('utf8'), file)
    }
    expect(json(LOGIN_FILE)['accessToken']).toBe('static-replay')
    for (const language of ['bn', 'en'] as const) {
      expect(json(metaFile(language))['audit']).toBeNull()
      expect(json(metaFile(language))['recordedDemo']).not.toBeNull()
      expect(json(analysisFile(language))['reviewTicket']).toBeNull()
    }
  })

  it('FR-85 records each removal it made, and no other', () => {
    expect(manifest.redactions.map((r) => `${r.file}:${r.path}`).sort()).toEqual(
      [
        'analysis.bn.json:reviewTicket',
        'analysis.en.json:reviewTicket',
        'login.worker.json:accessToken',
        'meta.bn.json:audit',
        'meta.en.json:audit',
      ].sort(),
    )
  })

  it('FR-85 memo files are PDFs', () => {
    for (const language of ['bn', 'en'] as const) {
      expect(read(memoFile(language)).subarray(0, 4).toString('latin1')).toBe('%PDF')
    }
  })
})
