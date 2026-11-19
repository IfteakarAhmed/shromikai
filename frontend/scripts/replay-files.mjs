// File names of the static recorded replay (FR-85, ADR-0027). The exporter
// (scripts/export-replay.mjs) writes these names under public/replay/ and the app
// (src/api/replay.ts) reads them, so one module decides them.

/** The POST /auth/demo/worker response (the session cookie header is not exported). */
export const LOGIN_FILE = 'login.worker.json'
/** The 202 answer of POST /analyses for the sample. */
export const ACCEPTED_FILE = 'analysis-accepted.json'
/** Integrity list: every exported file with its source request and SHA-256. */
export const MANIFEST_FILE = 'manifest.json'

export const metaFile = (language) => `meta.${language}.json`
export const analysisFile = (language) => `analysis.${language}.json`
export const traceFile = (language) => `trace.${language}.json`
export const memoFile = (language) => `memo.${language}.pdf`

const slug = (value) =>
  String(value)
    .replace(/[^A-Za-z0-9.-]/g, '-')
    .replace(/\.{2,}/g, '-')

/** One section of the corpus as a citation names it: act code, section number, version label. */
export const sectionFile = (actCode, sectionNo, versionLabel, language) =>
  `laws/${slug(actCode)}_${slug(sectionNo)}_${slug(versionLabel)}.${language}.json`
