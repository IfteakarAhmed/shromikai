// The pure parts of the static-replay exporter (FR-85, ADR-0027): what to fetch, what to remove
// before a response is published, and the checks that stop a secret from being saved. The network
// part is scripts/export-replay.mjs. A response is saved as the backend sent it; the only changes
// are the removals below, each one recorded in the manifest.
import { createHash } from 'node:crypto'

export const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex')

const isRecord = (value) => typeof value === 'object' && value !== null

/** Every distinct {actCode, sectionNo, versionLabel} a result cites, in a stable order. */
export function collectSectionRefs(value) {
  const seen = new Map()
  const walk = (node) => {
    if (Array.isArray(node)) {
      node.forEach(walk)
    } else if (isRecord(node)) {
      if (
        typeof node.actCode === 'string' &&
        typeof node.sectionNo === 'string' &&
        typeof node.versionLabel === 'string'
      ) {
        const ref = {
          actCode: node.actCode,
          sectionNo: node.sectionNo,
          versionLabel: node.versionLabel,
        }
        seen.set(`${ref.actCode}\u0000${ref.sectionNo}\u0000${ref.versionLabel}`, ref)
      }
      Object.values(node).forEach(walk)
    }
  }
  walk(value)
  return [...seen.entries()].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)).map(([, ref]) => ref)
}

/** The audit counters come from the audit and review tables, which are private: null them. */
export function redactMeta(meta) {
  if (!isRecord(meta) || meta.audit === null || meta.audit === undefined) {
    return { value: meta, redactions: [] }
  }
  return {
    value: { ...meta, audit: null },
    redactions: [
      {
        path: 'audit',
        reason: 'counters read from the audit and review tables of the development database',
      },
    ],
  }
}

/** A session token is never published; the replay does not check it. */
export function redactLogin(login) {
  if (!isRecord(login) || typeof login.accessToken !== 'string') {
    return { value: login, redactions: [] }
  }
  return {
    value: { ...login, accessToken: 'static-replay' },
    redactions: [{ path: 'accessToken', reason: 'a session token is never published' }],
  }
}

/** The review ticket's secret code belongs to the development database: the field becomes null. */
export function redactResult(result) {
  if (!isRecord(result) || result.reviewTicket === null || result.reviewTicket === undefined) {
    return { value: result, redactions: [] }
  }
  return {
    value: { ...result, reviewTicket: null },
    redactions: [
      {
        path: 'reviewTicket',
        reason: 'the one-time ticket code comes from the development database',
      },
    ],
  }
}

const JWT = /eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{6,}\.[A-Za-z0-9_-]{6,}/
const SECRET_FIELD = /"(refreshToken|refresh_token|password|jwtSecret|apiKey)"\s*:/i

/** Throws when a file that is about to be saved holds a JWT or a secret-looking field. */
export function assertNoSecrets(text, name) {
  if (JWT.test(text)) {
    throw new Error(`${name}: holds a JSON Web Token; refusing to save it`)
  }
  if (SECRET_FIELD.test(text)) {
    throw new Error(`${name}: holds a secret-looking field; refusing to save it`)
  }
}
