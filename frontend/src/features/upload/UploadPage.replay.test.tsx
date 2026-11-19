import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { sampleMeta } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { UploadPage } from './UploadPage'

beforeEach(() => {
  vi.stubEnv('VITE_STATIC_REPLAY', '1')
  server.use(
    http.get('/replay/meta.bn.json', () =>
      HttpResponse.json({ ...sampleMeta, recordedDemo: { side: 'WORKER', outputLanguage: 'bn' } }),
    ),
    http.get('/replay/login.worker.json', () =>
      HttpResponse.json({
        accessToken: 'stub',
        expiresIn: 900,
        guest: false,
        tokenType: 'Bearer',
        user: { id: 'u', email: 'w@example.test', role: 'WORKER' },
      }),
    ),
  )
})
afterEach(() => {
  vi.unstubAllEnvs()
})

describe('FR-85 upload page in the static recorded replay', () => {
  it('FR-85 says that no document can be uploaded and keeps the file choice disabled', async () => {
    renderApp(
      <Routes>
        <Route path="/app" element={<UploadPage />} />
      </Routes>,
      '/app',
    )
    expect(
      await screen.findByText(
        'স্ট্যাটিক রিপ্লেতে কোনো নথি আপলোড বা বিশ্লেষণ করা যায় না; শুধু রেকর্ড করা নমুনাটি দেখা যায়।',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'কীভাবে দেবেন' })).toBeDisabled()
  })
})
