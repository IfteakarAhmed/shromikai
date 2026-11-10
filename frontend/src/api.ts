// Typed client for the ShromikAI backend. In development Vite proxies /api to localhost:8080.

export interface CorpusStats {
  acts: number
  sections: number
  currentSections: number
  embeddedSections: number
}

export interface Meta {
  name: string
  version: string
  corpus: CorpusStats
}

const BASE = import.meta.env.VITE_API_BASE ?? ''

export async function getMeta(): Promise<Meta> {
  const res = await fetch(`${BASE}/api/v1/meta`)
  if (!res.ok) throw new Error(`Backend error ${res.status}`)
  return res.json()
}
