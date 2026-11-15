import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ALLOWED_STORAGE_KEYS } from '../../lib/storage'
import { makeResult } from '../../test/fixtures'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { AnalysisPage } from './AnalysisPage'

describe('StorageGuard', () => {
  it('R-02 after a full result, web storage holds only the device token and preferences', async () => {
    const result = makeResult()
    server.use(http.get(`/api/v1/analyses/${result.analysisId}`, () => HttpResponse.json(result)))
    renderApp(
      <Routes>
        <Route path="/app/analyses/:id" element={<AnalysisPage />} />
      </Routes>,
      `/app/analyses/${result.analysisId}`,
    )
    await screen.findByText('Dismissal without notice')

    const keys = Object.keys(window.localStorage)
    for (const key of keys) {
      expect(ALLOWED_STORAGE_KEYS).toContain(key)
    }
    const everything = JSON.stringify([
      Object.entries(window.localStorage),
      Object.entries(window.sessionStorage),
    ])
    expect(everything).not.toContain('Dismissal without notice')
    expect(everything).not.toContain(result.analysisId)
    expect(everything).not.toContain(result.envelope.signature)
    expect(everything).not.toContain('ticket-token')
    expect(window.sessionStorage.length).toBe(0)
    expect('indexedDB' in window ? window.indexedDB : undefined).toBeFalsy()
  })
})
