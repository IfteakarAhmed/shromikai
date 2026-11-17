import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import { sampleMeta } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { UploadPage } from './UploadPage'
import * as analyses from '../../api/analyses'

vi.mock('../../api/analyses', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/analyses')>()),
  startAnalysis: vi.fn(),
}))
vi.mock('../../lib/image', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../lib/image')>()),
  compressImage: (file: File) => Promise.resolve(file),
}))

const start = vi.mocked(analyses.startAnalysis)

function page(route = '/app') {
  return renderApp(
    <Routes>
      <Route path="/app" element={<UploadPage />} />
      <Route path="/app/analyses/:id" element={<p>analysis page</p>} />
    </Routes>,
    route,
  )
}

const ACCEPTED = {
  analysisId: '11111111-1111-4111-8111-111111111111',
  status: 'QUEUED' as const,
  eventsUrl: '/e',
  resultUrl: '/r',
  deadlineAt: '2026-11-16T10:02:00Z',
}

function fileInput(): HTMLInputElement {
  const input = document.querySelector<HTMLInputElement>('input[type=file]:not([capture])')
  if (input === null) {
    throw new Error('no file input')
  }
  return input
}

beforeEach(() => {
  start.mockReset()
  start.mockResolvedValue(ACCEPTED)
})

describe('UploadPage', () => {
  it('FR-69 asks for input before sending anything, in Bangla', async () => {
    page()

    await userEvent.click(screen.getByRole('button', { name: 'বিশ্লেষণ করুন' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('আগে একটি ফাইল, ছবি বা লেখা দিন।')
    expect(start).not.toHaveBeenCalled()
  })

  it('FR-69 refuses a text that is too short and says how many characters are needed', async () => {
    page()
    await userEvent.click(screen.getByRole('tab', { name: 'লেখা পেস্ট' }))

    await userEvent.type(screen.getByLabelText('চুক্তির লেখা'), 'খুব ছোট লেখা')
    await userEvent.click(screen.getByRole('button', { name: 'বিশ্লেষণ করুন' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('কমপক্ষে ২০০ অক্ষর')
    expect(start).not.toHaveBeenCalled()
  })

  it('FR-69 refuses a PDF over 10 MB with a Bangla message', async () => {
    page()
    const big = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'big.pdf', {
      type: 'application/pdf',
    })

    await userEvent.upload(fileInput(), big)

    expect(await screen.findByRole('alert')).toHaveTextContent('১০ এমবির বেশি')
  })

  it('FR-69 refuses a type the API cannot read', async () => {
    const user = userEvent.setup({ applyAccept: false })
    page()

    await user.upload(
      fileInput(),
      new File(['x'], 'contract.docx', { type: 'application/vnd.ms-word' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('এই ধরনের ফাইল নেওয়া যায় না')
  })

  it('FR-69 sends a valid text for the worker, in the interface language, and follows the job', async () => {
    page()
    await userEvent.click(screen.getByRole('tab', { name: 'লেখা পেস্ট' }))
    const text = 'এই চুক্তি কর্মী ও নিয়োগকর্তার মধ্যে। '.repeat(10)

    await userEvent.click(screen.getByLabelText('চুক্তির লেখা'))
    await userEvent.paste(text)
    await userEvent.click(screen.getByRole('button', { name: 'বিশ্লেষণ করুন' }))

    expect(await screen.findByText('analysis page')).toBeInTheDocument()
    const [input, options] = start.mock.calls[0] ?? []
    expect(input).toEqual({ kind: 'text', text: text.trim() })
    expect(options).toMatchObject({ side: 'WORKER', outputLanguage: 'bn' })
  })

  it('FR-69 sends the employer side when it is chosen', async () => {
    page()

    await userEvent.click(screen.getByRole('radio', { name: /আমি নিয়োগকর্তা/ }))
    await userEvent.click(screen.getByRole('button', { name: 'নমুনা চুক্তি দিয়ে দেখুন' }))

    await screen.findByText('analysis page')
    expect(start.mock.calls[0]?.[0]).toEqual({ kind: 'sample' })
    expect(start.mock.calls[0]?.[1]).toMatchObject({ side: 'EMPLOYER' })
  })

  it('FR-69 the sample runs with one tap and nothing to upload', async () => {
    page()

    await userEvent.click(screen.getByRole('button', { name: 'নমুনা চুক্তি দিয়ে দেখুন' }))

    expect(await screen.findByText('analysis page')).toBeInTheDocument()
    expect(start).toHaveBeenCalledOnce()
  })

  it('FR-68 starts the sample at once when the landing page sent the visitor here for it', async () => {
    renderApp(
      <Routes>
        <Route path="/app" element={<UploadPage />} />
        <Route path="/app/analyses/:id" element={<p>analysis page</p>} />
      </Routes>,
      { pathname: '/app', state: { autoSample: true } },
    )

    expect(await screen.findByText('analysis page')).toBeInTheDocument()
    expect(start).toHaveBeenCalledOnce()
    expect(start.mock.calls[0]?.[0]).toEqual({ kind: 'sample' })
  })

  it('FR-69 lets the reader reorder and delete the pages of a photo set', async () => {
    page()
    await userEvent.click(screen.getByRole('tab', { name: 'ছবি বা ক্যামেরা' }))
    const gallery = document.querySelector<HTMLInputElement>('input[type=file][multiple]')
    expect(gallery).not.toBeNull()
    await userEvent.upload(gallery as HTMLInputElement, [
      new File(['a'], 'first.jpg', { type: 'image/jpeg' }),
      new File(['b'], 'second.jpg', { type: 'image/jpeg' }),
      new File(['c'], 'third.png', { type: 'image/png' }),
    ])

    const pages = () => screen.getAllByRole('listitem').map((li) => li.textContent)
    expect(pages()[0]).toContain('first.jpg')

    await userEvent.click(screen.getByRole('button', { name: 'পৃষ্ঠা ১ নিচে নিন' }))
    expect(pages()[0]).toContain('second.jpg')
    expect(pages()[1]).toContain('first.jpg')

    await userEvent.click(screen.getByRole('button', { name: 'পৃষ্ঠা ২ সরান' }))
    expect(pages()).toHaveLength(2)
    expect(pages().join(' ')).not.toContain('first.jpg')
  })

  it('FR-69 refuses more than fifteen photos', async () => {
    page()
    await userEvent.click(screen.getByRole('tab', { name: 'ছবি বা ক্যামেরা' }))
    const gallery = document.querySelector<HTMLInputElement>('input[type=file][multiple]')
    const photos = Array.from(
      { length: 16 },
      (_, i) => new File(['x'], `p${i}.jpg`, { type: 'image/jpeg' }),
    )
    await userEvent.upload(gallery as HTMLInputElement, photos)

    await userEvent.click(screen.getByRole('button', { name: 'বিশ্লেষণ করুন' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('সর্বোচ্চ ১৫টি ছবি')
    expect(start).not.toHaveBeenCalled()
  })

  it('FR-69 shows the server problem in plain words when starting fails', async () => {
    start.mockRejectedValue(
      new ApiError(429, {
        type: 'urn:shromikai:problem:quota-exceeded',
        title: 'x',
        status: 429,
        instance: '/x',
        code: 'quota-exceeded',
        traceId: 't',
      }),
    )
    page()

    await userEvent.click(screen.getByRole('button', { name: 'নমুনা চুক্তি দিয়ে দেখুন' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('আজকের সীমা শেষ')
  })

  it('R-12 in a recorded demo only the sample can be analysed, from the recorded side and language', async () => {
    server.use(
      http.get('/api/v1/meta', () =>
        HttpResponse.json({
          ...sampleMeta,
          recordedDemo: { side: 'WORKER', outputLanguage: 'bn' },
        }),
      ),
    )
    page()

    expect(await screen.findByText(/শুধু নমুনা চুক্তিটি বিশ্লেষণ করা যায়/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'বিশ্লেষণ করুন' })).toBeDisabled()
    expect(screen.getByRole('radio', { name: /আমি নিয়োগকর্তা/ })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'নমুনা চুক্তি দিয়ে দেখুন' }))

    await screen.findByText('analysis page')
    expect(start.mock.calls[0]?.[1]).toMatchObject({ side: 'WORKER', outputLanguage: 'bn' })
  })

  it('FR-69 not-recorded is explained, not shown as a crash', async () => {
    start.mockRejectedValue(
      new ApiError(422, {
        type: 'urn:shromikai:problem:not-recorded',
        title: 'x',
        status: 422,
        instance: '/x',
        code: 'not-recorded',
        traceId: 't',
      }),
    )
    page()

    await userEvent.click(screen.getByRole('button', { name: 'নমুনা চুক্তি দিয়ে দেখুন' }))

    expect(
      within(await screen.findByRole('alert')).getByText(/রেকর্ড করা উত্তর নেই/),
    ).toBeInTheDocument()
  })

  it('FR-69 is three numbered steps in order: the contract, the side, start', async () => {
    page()
    const steps = await screen.findByRole('group', { name: 'তিন ধাপে চুক্তি দিন' })
    const items = within(steps).getAllByRole('region')
    expect(
      within(steps)
        .getAllByRole('heading', { level: 2 })
        .map((h) => h.textContent),
    ).toEqual(['চুক্তি দিন', 'কার পক্ষ থেকে দেখবেন', 'শুরু করুন'])
    const [first, second, third] = items
    expect(within(first as HTMLElement).getByRole('tablist')).toBeInTheDocument()
    expect(within(second as HTMLElement).getAllByRole('radio')).toHaveLength(2)
    expect(
      within(third as HTMLElement).getByRole('button', { name: 'বিশ্লেষণ করুন' }),
    ).toBeInTheDocument()
  })
})
