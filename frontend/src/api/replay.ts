import {
  ACCEPTED_FILE,
  LOGIN_FILE,
  analysisFile,
  memoFile,
  metaFile,
  sectionFile,
  traceFile,
  type ReplayLanguage,
} from '../../scripts/replay-files.mjs'
import { ApiError, NetworkError, type Problem } from './errors'

/**
 * The static recorded replay (FR-85, ADR-0027, R-12). A build with VITE_STATIC_REPLAY=1 has no
 * server: this module answers the API layer from files that `scripts/export-replay.mjs` saved from
 * the real responses of a running demo backend (profiles demo and fake-models) for one flow, the
 * recorded sample contract. Nothing is computed or written by hand here: a request that no exported
 * file answers gets an honest problem, never an invented result.
 */
export function isStaticReplay(): boolean {
  return import.meta.env.VITE_STATIC_REPLAY === '1'
}

/** Whether the recorded demo session is open; kept in memory only, like the real access token. */
let signedIn = false

/** Forgets the recorded session (sign-out, and tests). */
export function resetReplaySession(): void {
  signedIn = false
}

function problem(status: number, code: string, title: string): ApiError {
  const body: Problem = {
    type: `urn:shromikai:problem:${code}`,
    title,
    status,
    instance: '/replay',
    code,
    traceId: 'static-replay',
  }
  return new ApiError(status, body)
}

/** A live feature or a document the replay did not record. */
function unavailable(): ApiError {
  return problem(
    501,
    'static-replay-unavailable',
    'Not available in the static recorded replay: it needs a live server',
  )
}

function languageOf(language: string): ReplayLanguage {
  return language === 'en' ? 'en' : 'bn'
}

async function load(file: string): Promise<Response> {
  let response: Response
  try {
    response = await fetch(`${import.meta.env.BASE_URL}replay/${file}`)
  } catch {
    throw new NetworkError()
  }
  if (!response.ok) {
    throw unavailable()
  }
  return response
}

async function loadJson<T>(file: string): Promise<T> {
  return (await (await load(file)).json()) as T
}

/** The ID of the one recorded analysis, from the recorded 202 answer. */
async function recordedAnalysisId(): Promise<string> {
  return (await loadJson<{ analysisId: string }>(ACCEPTED_FILE)).analysisId
}

async function requireRecorded(id: string): Promise<void> {
  if (id !== (await recordedAnalysisId())) {
    throw problem(404, 'not-found', 'No such analysis in the static recorded replay')
  }
}

/** Answers one JSON API call from the exported files, or with a problem document. */
export async function replayRequest<T>(
  method: string,
  path: string,
  language: string,
  body?: string | FormData,
): Promise<T> {
  const lang = languageOf(language)
  const url = new URL(path, 'http://replay.invalid')
  const segments = url.pathname.split('/').filter((part) => part !== '')
  const [, , area, id, rest] = segments // api, v1, area, id, rest
  const get = method === 'GET'
  const post = method === 'POST'

  if (get && area === 'meta' && id === undefined) {
    return loadJson<T>(metaFile(lang))
  }
  if (post && area === 'auth') {
    if (id === 'demo' && rest === 'worker') {
      signedIn = true
      return loadJson<T>(LOGIN_FILE)
    }
    if (id === 'guest') {
      // Only one session was recorded: a guest gets it.
      signedIn = true
      return loadJson<T>(LOGIN_FILE)
    }
    if (id === 'refresh') {
      if (!signedIn) {
        throw problem(401, 'unauthorized', 'No recorded session is open')
      }
      return loadJson<T>(LOGIN_FILE)
    }
    if (id === 'logout') {
      signedIn = false
      return undefined as T
    }
  }
  if (post && area === 'analyses' && id === undefined) {
    // Only the recorded sample is available: any document part means another contract.
    if (!(body instanceof FormData) || ['text', 'file', 'images'].some((part) => body.has(part))) {
      throw unavailable()
    }
    return loadJson<T>(ACCEPTED_FILE)
  }
  if (get && area === 'analyses' && id !== undefined) {
    if (rest === undefined) {
      await requireRecorded(id)
      return loadJson<T>(analysisFile(lang))
    }
    if (rest === 'trace') {
      await requireRecorded(id)
      return loadJson<T>(traceFile(lang))
    }
  }
  if (get && area === 'laws' && id !== undefined && rest !== undefined) {
    const version = url.searchParams.get('version')
    if (version !== null) {
      const file = sectionFile(decodeURIComponent(id), decodeURIComponent(rest), version, lang)
      let response: Response
      try {
        response = await fetch(`${import.meta.env.BASE_URL}replay/${file}`)
      } catch {
        throw new NetworkError()
      }
      if (!response.ok) {
        throw problem(404, 'not-found', 'This section was not recorded in the static replay')
      }
      return (await response.json()) as T
    }
  }
  throw unavailable()
}

/** The recorded memo PDF of the reader's language (the only memo format that was recorded). */
export async function replayBlob(
  path: string,
  language: string,
  body: unknown,
): Promise<{ blob: Blob; contentType: string }> {
  const format = (body as { format?: string } | null)?.format
  if (!path.startsWith('/api/v1/memos/render') || format !== 'PDF') {
    throw unavailable()
  }
  const response = await load(memoFile(languageOf(language)))
  return {
    blob: await response.blob(),
    contentType: response.headers.get('Content-Type') ?? 'application/pdf',
  }
}
