// Saves the real responses of a running demo backend (profiles demo and fake-models) as the static
// files of the recorded replay (FR-85, ADR-0027). Nothing is written by hand and nothing is made up:
// every file is a response of the backend for the sample-contract flow, saved as it came, except
// for the removals listed in scripts/replay-export-core.mjs (each recorded in the manifest).
//
//   cd frontend && npm run export:replay                # backend on http://localhost:8080
//   npm run export:replay -- --base http://localhost:8080 --out public/replay
//
// The backend must run the recorded models (profile fake-models): the exporter refuses a backend
// whose meta has no recordedDemo, and a result that is not the recorded sample. It does not stop or
// restart the backend. The exported files are committed, because the static build has no backend.
import { execFileSync } from 'node:child_process'
import { mkdirSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  ACCEPTED_FILE,
  LOGIN_FILE,
  MANIFEST_FILE,
  analysisFile,
  memoFile,
  metaFile,
  sectionFile,
  traceFile,
} from './replay-files.mjs'
import {
  assertNoSecrets,
  collectSectionRefs,
  redactLogin,
  redactMeta,
  redactResult,
  sha256,
} from './replay-export-core.mjs'

const LANGUAGES = ['bn', 'en']
const here = dirname(fileURLToPath(import.meta.url))

function argument(name, fallback) {
  const index = process.argv.indexOf(`--${name}`)
  return index >= 0 && process.argv[index + 1] !== undefined ? process.argv[index + 1] : fallback
}

const BASE = argument('base', 'http://localhost:8080').replace(/\/$/, '')
const OUT = argument('out', join(here, '..', 'public', 'replay'))
// Everything is written next to the target and moved into place only when the whole export worked.
const STAGE = `${OUT}.new`

const files = []
const redactions = []

function save(path, bytes, request, language) {
  const data = typeof bytes === 'string' ? new TextEncoder().encode(bytes) : bytes
  if (path.endsWith('.json')) {
    assertNoSecrets(new TextDecoder().decode(data), path)
  }
  const target = join(STAGE, path)
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, data)
  files.push({
    path,
    request,
    language: language ?? null,
    bytes: data.length,
    sha256: sha256(data),
  })
}

function saveJson(path, value, request, language, applied = []) {
  save(path, `${JSON.stringify(value, null, 2)}\n`, request, language)
  for (const redaction of applied) {
    redactions.push({ file: path, ...redaction })
  }
}

async function call(method, path, { language = 'bn', token, body, accept, contentType } = {}) {
  const headers = { 'Accept-Language': language }
  if (accept) headers.Accept = accept
  if (contentType) headers['Content-Type'] = contentType
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { method, headers, body })
  return response
}

async function json(method, path, options) {
  const response = await call(method, path, options)
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`${method} ${path} answered ${response.status}: ${text.slice(0, 300)}`)
  }
  return { status: response.status, value: JSON.parse(text) }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function main() {
  // 1. meta: the backend must be on recorded models.
  const metas = {}
  for (const language of LANGUAGES) {
    const { value } = await json('GET', '/api/v1/meta', { language })
    if (value.recordedDemo === null || value.recordedDemo === undefined) {
      throw new Error(
        'the backend is not on recorded models (meta.recordedDemo is empty): run it with the demo and fake-models profiles',
      )
    }
    metas[language] = value
    const { value: safe, redactions: applied } = redactMeta(value)
    saveJson(metaFile(language), safe, 'GET /api/v1/meta', language, applied)
  }

  // 2. one-click demo login of the worker (the seeded demo account).
  const login = await json('POST', '/api/v1/auth/demo/worker')
  const token = login.value.accessToken
  {
    const { value, redactions: applied } = redactLogin(login.value)
    saveJson(LOGIN_FILE, value, 'POST /api/v1/auth/demo/worker', null, applied)
  }

  // 3. the sample analysis: multipart, exactly as the upload page sends it.
  const options = {
    side: metas.bn.recordedDemo.side,
    outputLanguage: metas.bn.recordedDemo.outputLanguage,
    clientKind: 'DESKTOP_WEB',
    sample: true,
    playbookId: null,
    profile: null,
    workerCategory: null,
  }
  const form = new FormData()
  form.append('options', new Blob([JSON.stringify(options)], { type: 'application/json' }))
  const accepted = await json('POST', '/api/v1/analyses', { token, body: form })
  if (accepted.status !== 202) {
    throw new Error(`POST /api/v1/analyses answered ${accepted.status}, expected 202`)
  }
  saveJson(ACCEPTED_FILE, accepted.value, 'POST /api/v1/analyses (sample)', null)
  const id = accepted.value.analysisId

  // 4. the result in both languages (same analysis), once the run has finished, and what it cites.
  const results = {}
  for (const language of LANGUAGES) {
    let found = null
    for (let attempt = 0; attempt < 60 && found === null; attempt += 1) {
      const response = await json('GET', `/api/v1/analyses/${id}`, { token, language })
      if (response.status === 200 && 'findings' in response.value) found = response.value
      else await sleep(1000)
    }
    if (found === null) throw new Error(`the result did not arrive in ${language}`)
    if (found.provenance?.recorded !== true || found.input?.sample !== true) {
      throw new Error(
        'the result is not the recorded sample (provenance.recorded and input.sample)',
      )
    }
    results[language] = found
    const { value, redactions: applied } = redactResult(found)
    saveJson(analysisFile(language), value, `GET /api/v1/analyses/${id}`, language, applied)
  }
  const sections = collectSectionRefs(results.bn)

  // 5. the "why" sheet's trace.
  for (const language of LANGUAGES) {
    const { value } = await json('GET', `/api/v1/analyses/${id}/trace`, { token, language })
    saveJson(traceFile(language), value, `GET /api/v1/analyses/${id}/trace`, language)
  }

  // 6. every cited section, as the citation sheet reads it.
  for (const language of LANGUAGES) {
    for (const ref of sections) {
      const query = new URLSearchParams({ version: ref.versionLabel })
      const path = `/api/v1/laws/${encodeURIComponent(ref.actCode)}/${encodeURIComponent(ref.sectionNo)}?${query}`
      const { value } = await json('GET', path, { token, language })
      saveJson(
        sectionFile(ref.actCode, ref.sectionNo, ref.versionLabel, language),
        value,
        `GET ${path}`,
        language,
      )
    }
  }

  // 7. the memo as a PDF, rendered by the backend (Gotenberg) from the signed result.
  for (const language of LANGUAGES) {
    const { envelope, ...payload } = results[language]
    const rendered = await call('POST', '/api/v1/memos/render', {
      language,
      token,
      contentType: 'application/json',
      body: JSON.stringify({
        envelope: {
          payload,
          keyId: envelope.keyId,
          algorithm: envelope.algorithm,
          expiresAt: envelope.expiresAt,
          signature: envelope.signature,
        },
        format: 'PDF',
      }),
    })
    const bytes = new Uint8Array(await rendered.arrayBuffer())
    if (!rendered.ok || new TextDecoder().decode(bytes.slice(0, 4)) !== '%PDF') {
      throw new Error(`the memo for ${language} is not a PDF (status ${rendered.status})`)
    }
    save(memoFile(language), bytes, 'POST /api/v1/memos/render (PDF)', language)
  }

  // 8. the manifest: what was saved, from which request, and what was removed.
  const git = (args) => {
    try {
      return execFileSync('git', args, { cwd: here, encoding: 'utf8' }).trim()
    } catch {
      return null
    }
  }
  const manifest = {
    purpose:
      'Real responses of a demo backend (profiles demo and fake-models) for the recorded sample contract; the static recorded replay serves them (ADR-0027). Not written by hand.',
    generatedAt: new Date().toISOString(),
    gitCommit: git(['rev-parse', 'HEAD']),
    gitDirty: (git(['status', '--porcelain']) ?? '') !== '',
    backend: {
      name: metas.bn.name,
      version: metas.bn.version,
      corpusRelease: metas.bn.corpus?.release ?? metas.bn.corpus ?? null,
      models: metas.bn.models,
      recordedDemo: metas.bn.recordedDemo,
    },
    analysisId: id,
    citedSections: sections.length,
    redactions,
    files: files.sort((a, b) => (a.path < b.path ? -1 : 1)),
  }
  writeFileSync(join(STAGE, MANIFEST_FILE), `${JSON.stringify(manifest, null, 2)}\n`)
  rmSync(OUT, { recursive: true, force: true })
  renameSync(STAGE, OUT)
  console.log(`exported ${files.length} files to ${relative(process.cwd(), OUT)}`)
  console.log(`analysis ${id}: ${results.bn.findings.length} findings, ${sections.length} sections`)
}

rmSync(STAGE, { recursive: true, force: true })
mkdirSync(STAGE, { recursive: true })
main().catch((error) => {
  rmSync(STAGE, { recursive: true, force: true })
  console.error(`export-replay: ${error.message}; the previous files were kept`)
  process.exit(1)
})
