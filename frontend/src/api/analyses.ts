import { apiFetchBlob, apiGet, apiPost, apiPostForm, apiSend } from './client'
import type { PollResult } from './sse'
import type { components } from './types'

export type AnalysisResult = components['schemas']['AnalysisResult']
export type AnalysisAccepted = components['schemas']['AnalysisAccepted']
export type AnalysisStatusView = components['schemas']['AnalysisStatusView']
export type AnalysisTrace = components['schemas']['AnalysisTrace']
export type ObligationsExport = components['schemas']['ObligationsExport']
export type RiskFinding = components['schemas']['RiskFinding']
export type VerifiedCitation = components['schemas']['VerifiedCitation']
export type Redline = components['schemas']['Redline']
export type ObligationItem = components['schemas']['ObligationItem']
export type Memo = components['schemas']['Memo']
export type Section = components['schemas']['Section']
export type CompareResult = components['schemas']['CompareResult']
export type CompareAccepted = components['schemas']['CompareAccepted']
export type CompareRunning = components['schemas']['CompareRunning']
export type Side = 'WORKER' | 'EMPLOYER'
export type OutputLanguage = 'bn' | 'en'

/** What the visitor gave us: the bundled sample, pasted text, one PDF or photos of the pages. */
export type AnalysisInput =
  | { kind: 'sample' }
  | { kind: 'text'; text: string }
  | { kind: 'file'; file: File }
  | { kind: 'images'; files: readonly File[] }

export interface StartOptions {
  side: Side
  outputLanguage: OutputLanguage
  clientKind: 'MOBILE_WEB' | 'DESKTOP_WEB'
}

/**
 * Starts an analysis (E-12). Everything is sent as multipart, so the API always answers 202 with a
 * job to follow: the visitor watches the stages instead of waiting on one request.
 */
export function startAnalysis(
  input: AnalysisInput,
  options: StartOptions,
  language: string,
  token: string | null,
): Promise<AnalysisAccepted> {
  const form = new FormData()
  const meta: components['schemas']['AnalysisOptions'] = {
    side: options.side,
    outputLanguage: options.outputLanguage,
    clientKind: options.clientKind,
    sample: input.kind === 'sample',
    playbookId: null,
    profile: null,
    workerCategory: null,
  }
  form.append('options', new Blob([JSON.stringify(meta)], { type: 'application/json' }))
  switch (input.kind) {
    case 'sample':
      break
    case 'text':
      form.append('text', new Blob([input.text], { type: 'text/plain; charset=utf-8' }), 'text.txt')
      break
    case 'file':
      form.append('file', input.file, input.file.name)
      break
    case 'images':
      for (const image of input.files) {
        form.append('images', image, image.name)
      }
      break
  }
  return apiPostForm<AnalysisAccepted>('/api/v1/analyses', language, form, token ?? undefined)
}

/** Starts compare mode (E-18): JSON, sample or text only. */
export function startCompare(
  input: { kind: 'sample' } | { kind: 'text'; text: string },
  options: Pick<StartOptions, 'side' | 'outputLanguage'>,
  language: string,
  token: string | null,
): Promise<CompareAccepted> {
  const body =
    input.kind === 'sample'
      ? { sample: true, side: options.side, outputLanguage: options.outputLanguage }
      : { text: input.text, side: options.side, outputLanguage: options.outputLanguage }
  return apiPost<CompareAccepted>('/api/v1/compare', language, body, token ?? undefined)
}

/** The result of a finished analysis, or the status of one that is still running or failed. */
export async function getAnalysis(
  id: string,
  language: string,
  token: string | null,
): Promise<AnalysisResult | AnalysisStatusView> {
  return apiGet<AnalysisResult | AnalysisStatusView>(
    `/api/v1/analyses/${id}`,
    language,
    token ?? undefined,
  )
}

export function isResult(body: AnalysisResult | AnalysisStatusView): body is AnalysisResult {
  return 'findings' in body
}

/** One look at the job, in the shape the SSE client's polling fallback needs. */
export async function pollAnalysis(
  id: string,
  language: string,
  token: string | null,
): Promise<PollResult> {
  const body = await getAnalysis(id, language, token)
  if (isResult(body)) {
    return {
      state: 'completed',
      findingCount: body.findings.length,
      uncertainCount: body.summary.uncertainFindings,
    }
  }
  if (body.status === 'FAILED') {
    return { state: 'failed', problem: body.problem }
  }
  return { state: 'running', stage: body.stage, progress: body.progress }
}

export function getCompare(
  id: string,
  language: string,
  token: string | null,
): Promise<CompareResult | CompareRunning> {
  return apiGet<CompareResult | CompareRunning>(
    `/api/v1/compare/${id}`,
    language,
    token ?? undefined,
  )
}

export function isCompareResult(body: CompareResult | CompareRunning): body is CompareResult {
  return 'baseline' in body
}

export function getTrace(id: string, language: string, token: string | null) {
  return apiGet<AnalysisTrace>(`/api/v1/analyses/${id}/trace`, language, token ?? undefined)
}

export function getObligations(id: string, language: string, token: string | null) {
  return apiGet<ObligationsExport>(
    `/api/v1/analyses/${id}/obligations`,
    language,
    token ?? undefined,
  )
}

export function deleteAnalysis(id: string, language: string, token: string | null) {
  return apiSend<undefined>(
    'DELETE',
    `/api/v1/analyses/${id}`,
    language,
    undefined,
    token ?? undefined,
  )
}

/**
 * The memo as a PDF or HTML from the server (E-21). The envelope is the result as returned, minus
 * its own `envelope` field, with the signature fields copied from it (api.md §3, SignedEnvelope).
 */
export function renderMemo(
  result: AnalysisResult,
  format: 'PDF' | 'HTML',
  language: string,
  token: string | null,
) {
  const { envelope, ...payload } = result
  return apiFetchBlob(
    '/api/v1/memos/render',
    language,
    {
      envelope: {
        payload,
        keyId: envelope.keyId,
        algorithm: envelope.algorithm,
        expiresAt: envelope.expiresAt,
        signature: envelope.signature,
      },
      format,
    },
    token ?? undefined,
  )
}
