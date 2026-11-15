import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { makeCompare } from '../../test/fixtures'
import { sampleMeta } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { CompareResultPage, CompareStartPage } from './ComparePage'
import { CompareResultView } from './CompareResultView'

afterEach(() => {
  vi.restoreAllMocks()
})

function wide(matches: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: matches && query.includes('min-width'),
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  })) as unknown as typeof window.matchMedia
}

describe('CompareResultView', () => {
  it('FR-72 writes the summary from the counts of the response, not from a fixed text', () => {
    wide(true)
    const { unmount } = renderApp(<CompareResultView compare={makeCompare()} />)
    expect(screen.getByTestId('compare-summary')).toHaveTextContent(
      'সাধারণ এআইয়ের ৫টি উদ্ধৃতির মধ্যে ৩টি বাতিল',
    )
    unmount()

    renderApp(
      <CompareResultView
        compare={makeCompare({
          summary: {
            baselineCitations: 12,
            baselineRejected: 7,
            baselineUncertain: 2,
            baselineVerified: 3,
            shromikaiShownCitations: 4,
          },
        })}
      />,
    )
    expect(screen.getByTestId('compare-summary')).toHaveTextContent(
      'সাধারণ এআইয়ের ১২টি উদ্ধৃতির মধ্যে ৭টি বাতিল',
    )
  })

  it("FR-72 shows rejected claims with a cross and the verifier's reason, as claims and not as citations", () => {
    wide(true)
    renderApp(<CompareResultView compare={makeCompare()} />)

    const column = screen.getByRole('region', { name: 'সাধারণ এআইয়ের উত্তর' })
    expect(within(column).getAllByText('✗ বাতিল')).toHaveLength(3)
    expect(within(column).getByText('ধারাটি আইনের সংগ্রহে পাওয়া যায়নি')).toBeInTheDocument()
    expect(within(column).getByText('উদ্ধৃত অংশ ধারায় পাওয়া যায়নি')).toBeInTheDocument()
    expect(within(column).getAllByText('এটি উদ্ধৃতি নয়, মডেলের দাবি।')).toHaveLength(5)
    // R-01 / ADR-0022: no chip, no link and no verified badge in the baseline column.
    expect(within(column).queryByRole('button')).not.toBeInTheDocument()
    expect(within(column).queryByRole('link')).not.toBeInTheDocument()
    expect(within(column).queryByText('✓ যাচাইকৃত')).not.toBeInTheDocument()
  })

  it('FR-72 shows two columns on a wide screen and two tabs on a phone', async () => {
    wide(true)
    const desktop = renderApp(<CompareResultView compare={makeCompare()} />)
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'যাচাইসহ ফল' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'সাধারণ এআইয়ের উত্তর' })).toBeInTheDocument()
    desktop.unmount()

    wide(false)
    renderApp(<CompareResultView compare={makeCompare()} />)
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual([
      'সাধারণ এআই',
      'যাচাইসহ',
    ])
    expect(screen.queryByRole('region', { name: 'যাচাইসহ ফল' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'যাচাইসহ' }))

    expect(screen.getByRole('region', { name: 'যাচাইসহ ফল' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'সাধারণ এআইয়ের উত্তর' })).not.toBeInTheDocument()
  })

  it('FR-72 R-01 our column keeps the strict rule: verified chips only', () => {
    wide(true)
    renderApp(<CompareResultView compare={makeCompare()} />)

    const ours = screen.getByRole('region', { name: 'যাচাইসহ ফল' })

    expect(within(ours).getAllByRole('button', { name: /ধারা \S+ খুলুন/ })).toHaveLength(1)
    expect(within(ours).getByText('মানুষের পর্যালোচনার জন্য পাঠানো হয়েছে')).toBeInTheDocument()
  })

  it('R-12 says when the baseline is a scripted example and when it is unavailable', () => {
    wide(true)
    const scripted = renderApp(
      <CompareResultView
        compare={makeCompare({
          baseline: { ...makeCompare().baseline, modelId: 'scripted-demo-standin' },
        })}
      />,
    )
    expect(screen.getByText(/স্ক্রিপ্ট করা উদাহরণ/)).toBeInTheDocument()
    scripted.unmount()

    renderApp(
      <CompareResultView
        compare={makeCompare({
          baseline: {
            status: 'UNAVAILABLE',
            reason: 'model-unavailable',
            modelId: null,
            findings: [],
          },
        })}
      />,
    )
    expect(screen.getByText('সাধারণ এআইয়ের উত্তর এখন পাওয়া যায়নি।')).toBeInTheDocument()
  })
})

describe('compare pages', () => {
  it('FR-59 starts a comparison on the sample and follows it to the result', async () => {
    wide(true)
    const id = '44444444-4444-4444-8444-444444444444'
    let body: unknown = null
    let polls = 0
    server.use(
      http.post('/api/v1/compare', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(
          {
            compareId: id,
            eventsUrl: `/api/v1/compare/${id}/events`,
            resultUrl: `/api/v1/compare/${id}`,
          },
          { status: 202 },
        )
      }),
      http.get(`/api/v1/compare/${id}`, () => {
        polls += 1
        return polls < 2
          ? HttpResponse.json({ compareId: id, status: 'RUNNING' }, { status: 202 })
          : HttpResponse.json(makeCompare())
      }),
      http.get(`/api/v1/compare/${id}/events`, () => new HttpResponse(null, { status: 503 })),
    )
    renderApp(
      <Routes>
        <Route path="/app/compare" element={<CompareStartPage />} />
        <Route path="/app/compare/:id" element={<CompareResultPage />} />
      </Routes>,
      '/app/compare',
    )

    await userEvent.click(screen.getByRole('button', { name: 'নমুনা চুক্তিতে তুলনা করুন' }))

    expect(await screen.findByTestId('compare-summary', {}, { timeout: 8000 })).toHaveTextContent(
      '৩টি বাতিল',
    )
    expect(body).toMatchObject({ sample: true, side: 'WORKER', outputLanguage: 'bn' })
  })

  it('R-12 in a recorded demo only the sample comparison can be started', async () => {
    server.use(
      http.get('/api/v1/meta', () =>
        HttpResponse.json({
          ...sampleMeta,
          recordedDemo: { side: 'WORKER', outputLanguage: 'bn' },
        }),
      ),
    )
    renderApp(<CompareStartPage />)

    expect(await screen.findByText(/শুধু নমুনা চুক্তিটি বিশ্লেষণ করা যায়/)).toBeInTheDocument()
    expect(screen.getByLabelText('নিজের লেখায় তুলনা করুন')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'নমুনা চুক্তিতে তুলনা করুন' })).toBeEnabled()
  })
})
