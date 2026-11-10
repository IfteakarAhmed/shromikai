import { http, HttpResponse } from 'msw'
import type { Meta } from '../../api/client'

/** Synthetic Meta response for the fictional test corpus. */
export const sampleMeta: Meta = {
  name: 'ShromikAI',
  version: '0.1.0',
  models: {
    reasoning: 'claude-sonnet-5-5',
    fast: 'claude-haiku-5-5',
    verifier: null,
    verifierFallback: 'claude-haiku-5-5',
    judge: null,
    localPii: 'qwen2.5:7b-instruct',
    embedding: 'bge-m3',
  },
  corpus: {
    release: null,
    acts: 3,
    sections: 120,
    currentSections: 112,
    embeddedSections: 112,
    lastRefreshAt: null,
  },
  audit: null,
}

export const handlers = [http.get('/api/v1/meta', () => HttpResponse.json(sampleMeta))]
