import { apiGet, apiPost, apiSend } from './client'
import type { components } from './types'

export type ReviewItem = components['schemas']['ReviewItem']
export type ReviewItemPage = components['schemas']['ReviewItemPage']
export type ReviewItemPatch = components['schemas']['ReviewItemPatch']
export type Playbook = components['schemas']['Playbook']
export type PlaybookInput = components['schemas']['PlaybookInput']
export type PlaybookRule = components['schemas']['PlaybookRule']
export type PlaybookPage = components['schemas']['PlaybookPage']
export type UsageReport = components['schemas']['UsageReport']
export type AnalysisTrace = components['schemas']['AnalysisTrace']
export type ApiKeyView = components['schemas']['ApiKeyView']
export type CreatedKey = components['schemas']['CreatedKey']

export interface ReviewFilter {
  status: string
  severity: string
  page: number
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text === '' ? '' : `?${text}`
}

/** E-23: the review queue, newest first, filtered by status and severity. */
export function listReviewItems(
  filter: ReviewFilter,
  language: string,
  token: string | null,
): Promise<ReviewItemPage> {
  return apiGet<ReviewItemPage>(
    `/api/v1/review-items${query({
      status: filter.status,
      severity: filter.severity,
      page: filter.page,
      size: 20,
    })}`,
    language,
    token ?? undefined,
  )
}

/** E-24 */
export function getReviewItem(
  id: string,
  language: string,
  token: string | null,
): Promise<ReviewItem> {
  return apiGet<ReviewItem>(`/api/v1/review-items/${id}`, language, token ?? undefined)
}

/** E-25: claim, release or decide an item. */
export function patchReviewItem(
  id: string,
  patch: ReviewItemPatch,
  language: string,
  token: string | null,
): Promise<ReviewItem> {
  return apiSend<ReviewItem>('PATCH', `/api/v1/review-items/${id}`, language, patch, token ?? undefined)
}

/** E-26 */
export function listPlaybooks(language: string, token: string | null): Promise<PlaybookPage> {
  return apiGet<PlaybookPage>('/api/v1/playbooks?size=100', language, token ?? undefined)
}

/** E-27 */
export function createPlaybook(
  input: PlaybookInput,
  language: string,
  token: string | null,
): Promise<Playbook> {
  return apiPost<Playbook>('/api/v1/playbooks', language, input, token ?? undefined)
}

/** E-29 */
export function updatePlaybook(
  id: string,
  input: PlaybookInput,
  language: string,
  token: string | null,
): Promise<Playbook> {
  return apiSend<Playbook>('PUT', `/api/v1/playbooks/${id}`, language, input, token ?? undefined)
}

/** E-30: archives the playbook. */
export function archivePlaybook(
  id: string,
  language: string,
  token: string | null,
): Promise<void> {
  return apiSend<void>('DELETE', `/api/v1/playbooks/${id}`, language, undefined, token ?? undefined)
}

/** E-37 */
export function listApiKeys(language: string, token: string | null): Promise<ApiKeyView[]> {
  return apiGet<ApiKeyView[]>('/api/v1/admin/api-keys', language, token ?? undefined)
}

export function createApiKey(
  name: string,
  scopes: readonly string[],
  language: string,
  token: string | null,
): Promise<CreatedKey> {
  return apiPost<CreatedKey>(
    '/api/v1/admin/api-keys',
    language,
    { name, scopes: [...scopes] },
    token ?? undefined,
  )
}

/** E-38: revokes the key. */
export function revokeApiKey(id: string, language: string, token: string | null): Promise<void> {
  return apiSend<void>(
    'DELETE',
    `/api/v1/admin/api-keys/${id}`,
    language,
    undefined,
    token ?? undefined,
  )
}

/** E-39: usage and cost per day with the KPI block. */
export function getUsage(
  range: { from: string; to: string } | null,
  language: string,
  token: string | null,
): Promise<UsageReport> {
  return apiGet<UsageReport>(
    `/api/v1/admin/usage${range === null ? '' : query({ from: range.from, to: range.to })}`,
    language,
    token ?? undefined,
  )
}

/** E-40: the content-free trace of any analysis. */
export function getAdminTrace(
  analysisId: string,
  language: string,
  token: string | null,
): Promise<AnalysisTrace> {
  return apiGet<AnalysisTrace>(
    `/api/v1/admin/traces/${analysisId}`,
    language,
    token ?? undefined,
  )
}
