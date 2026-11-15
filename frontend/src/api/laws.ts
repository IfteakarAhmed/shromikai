import { apiGet } from './client'
import type { Section } from './analyses'

/** One section of the statute corpus, in the version a citation names (E-34). */
export function getSection(
  actCode: string,
  sectionNo: string,
  versionLabel: string,
  language: string,
  token: string | null,
): Promise<Section> {
  const query = new URLSearchParams({ version: versionLabel })
  return apiGet<Section>(
    `/api/v1/laws/${encodeURIComponent(actCode)}/${encodeURIComponent(sectionNo)}?${query.toString()}`,
    language,
    token ?? undefined,
  )
}
