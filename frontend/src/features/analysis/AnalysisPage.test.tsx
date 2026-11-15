import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { makeCitation, makeFinding, makeResult, makeUncertainFinding } from '../../test/fixtures'
import { problem } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { AnalysisPage } from './AnalysisPage'
import { watchTiming } from './timing'

const ID = '44444444-4444-4444-8444-444444444444'

function page() {
  return renderApp(
    <Routes>
      <Route path="/app/analyses/:id" element={<AnalysisPage />} />
    </Routes>,
    `/app/analyses/${ID}`,
  )
}

function running(stage: string | null = null, progress = 0) {
  return HttpResponse.json(
    { analysisId: ID, status: 'RUNNING', stage, progress, problem: null },
    { status: 202 },
  )
}

const encoder = new TextEncoder()
function stageFrame(id: number, stage: string, progress: number) {
  return `id: ${id}\nevent: stage\ndata: ${JSON.stringify({
    analysisId: ID,
    stage,
    state: 'COMPLETED',
    progress,
    message: 'm',
    at: '2026-11-16T10:00:00.000Z',
  })}\n\n`
}
function completedFrame(id: number) {
  return `id: ${id}\nevent: completed\ndata: ${JSON.stringify({
    analysisId: ID,
    resultUrl: '/r',
    findingCount: 2,
    uncertainCount: 1,
  })}\n\n`
}

/** An event stream the test feeds by hand. */
function liveStream() {
  let controller: ReadableStreamDefaultController<Uint8Array> | null = null
  const body = new ReadableStream<Uint8Array>({
    start(c) {
      controller = c
    },
  })
  return {
    response: () => new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } }),
    push: (frame: string) => controller?.enqueue(encoder.encode(frame)),
    close: () => controller?.close(),
  }
}

const original = { ...watchTiming }
beforeEach(() => {
  watchTiming.pollIntervalMs = 10
  watchTiming.retryDelayMs = 1
})
afterEach(() => {
  Object.assign(watchTiming, original)
})

describe('AnalysisPage results', () => {
  it('FR-70 shows a finished analysis: counts, a verified and an uncertain risk, the legend and the disclaimer', async () => {
    server.use(http.get(`/api/v1/analyses/${ID}`, () => HttpResponse.json(makeResult())))

    page()

    expect(
      await screen.findByRole('heading', { name: 'আপনার চুক্তির পর্যালোচনা' }),
    ).toBeInTheDocument()
    expect(screen.getByTestId('total-findings')).toHaveTextContent('২')
    expect(screen.getByTestId('uncertain-findings')).toHaveTextContent('১')
    expect(screen.getByText('Dismissal without notice')).toBeInTheDocument()
    expect(screen.getByText('Resignation needs permission')).toBeInTheDocument()
    expect(screen.getAllByText('✓ যাচাইকৃত').length).toBeGreaterThan(0)
    expect(screen.getAllByText('? অনিশ্চিত').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /ধারা ৫ খুলুন/ })).toBeInTheDocument()
    expect(screen.getByText('মানুষের পর্যালোচনার জন্য পাঠানো হয়েছে')).toBeInTheDocument()
    expect(screen.getByText(/This is not legal advice/)).toBeInTheDocument()
  })

  it('R-12 labels a recorded sample result as a sample and as recorded', async () => {
    server.use(http.get(`/api/v1/analyses/${ID}`, () => HttpResponse.json(makeResult())))

    page()

    expect(await screen.findByText('কাল্পনিক নমুনা চুক্তি, বাস্তব নয়')).toBeInTheDocument()
    expect(screen.getAllByText('নমুনা তথ্য (recorded)').length).toBeGreaterThan(0)
  })

  it('FR-77 shows the models and prompt versions that made the result', async () => {
    server.use(http.get(`/api/v1/analyses/${ID}`, () => HttpResponse.json(makeResult())))
    page()
    await screen.findByText('Dismissal without notice')

    await userEvent.click(screen.getByText('কীভাবে হলো'))

    expect(screen.getByText(/recorded-model/)).toBeInTheDocument()
    expect(screen.getByText(/risk-analysis v2/)).toBeInTheDocument()
    expect(screen.getByText('xx-2026.11')).toBeInTheDocument()
  })

  it('FR-70 sorts the most serious risk first', async () => {
    const result = makeResult({
      findings: [
        makeFinding({ id: 'f1', severity: 'LOW', title: 'A minor point here' }),
        makeFinding({ id: 'f2', severity: 'CRITICAL', title: 'The critical point here' }),
      ],
      summary: {
        ...makeResult().summary,
        findingsBySeverity: { CRITICAL: 1, HIGH: 0, MEDIUM: 0, LOW: 1 },
      },
    })
    server.use(http.get(`/api/v1/analyses/${ID}`, () => HttpResponse.json(result)))
    page()

    const cards = await screen.findAllByRole('article')

    expect(within(cards[0] as HTMLElement).getByText('The critical point here')).toBeInTheDocument()
    expect(within(cards[1] as HTMLElement).getByText('A minor point here')).toBeInTheDocument()
  })

  it('R-01 an uncertain risk never shows a citation even if the response carries one', async () => {
    const result = makeResult({
      findings: [makeUncertainFinding({ citations: [makeCitation()] })],
    })
    server.use(http.get(`/api/v1/analyses/${ID}`, () => HttpResponse.json(result)))
    page()

    await screen.findByText('Resignation needs permission')

    expect(screen.queryByRole('button', { name: /ধারা \S+ খুলুন/ })).not.toBeInTheDocument()
  })

  it('FR-70 opens the citation sheet from a chip', async () => {
    server.use(
      http.get(`/api/v1/analyses/${ID}`, () => HttpResponse.json(makeResult())),
      http.get('/api/v1/laws/XX-ACT-1/5', () =>
        HttpResponse.json({
          sectionKey: 'XX-ACT-1:5@original',
          actCode: 'XX-ACT-1',
          actTitleBn: 'কাল্পনিক আইন (শুধু পরীক্ষার জন্য)',
          actTitleEn: 'Fictional Act (test only)',
          sectionNo: '5',
          sectionNoOriginal: '5',
          headingBn: null,
          headingEn: null,
          authoritativeLang: 'en',
          authoritativeText: `1. ${makeCitation().quote} End.`,
          translation: null,
          versionLabel: 'original',
          effectiveFrom: null,
          isCurrent: true,
          sourceUrl: 'https://example.invalid/xx/5',
          fetchedAt: '2026-11-01T00:00:00Z',
          contentHash: null,
          related: [],
          corpusRelease: null,
        }),
      ),
    )
    page()

    await userEvent.click(await screen.findByRole('button', { name: /ধারা ৫ খুলুন/ }))

    const dialog = await screen.findByRole('dialog')
    expect((await within(dialog).findByText(makeCitation().quote)).tagName).toBe('MARK')
  })
})

describe('AnalysisPage progress', () => {
  it('FR-58 shows the real stages in order as the stream reports them, then the result', async () => {
    const stream = liveStream()
    let done = false
    server.use(
      http.get(`/api/v1/analyses/${ID}`, () =>
        done ? HttpResponse.json(makeResult()) : running(),
      ),
      http.get(`/api/v1/analyses/${ID}/events`, () => stream.response()),
    )
    page()

    const steps = async () => (await screen.findAllByRole('listitem')).map((li) => li.textContent)
    await screen.findByRole('heading', { name: 'আপনার চুক্তি বিশ্লেষণ হচ্ছে' })
    expect((await steps())[0]).toContain('চলছে')

    stream.push(stageFrame(1, 'INTAKE', 5))
    await waitFor(async () => {
      const [first, second] = await steps()
      expect(first).toContain('শেষ')
      expect(second).toContain('চলছে')
    })

    stream.push(stageFrame(2, 'REDACT', 25))
    stream.push(stageFrame(3, 'CLASSIFY', 35))
    await waitFor(async () => {
      const list = await steps()
      expect(list[1]).toContain('শেষ')
      expect(list[2]).toContain('শেষ')
      expect(list[3]).toContain('চলছে')
    })
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '35')

    done = true
    stream.push(completedFrame(4))
    stream.close()

    expect(await screen.findByText('Dismissal without notice')).toBeInTheDocument()
  })

  it('FR-58 shows warnings the stream sends', async () => {
    const stream = liveStream()
    server.use(
      http.get(`/api/v1/analyses/${ID}`, () => running()),
      http.get(`/api/v1/analyses/${ID}/events`, () => stream.response()),
    )
    page()
    await screen.findByRole('heading', { name: 'আপনার চুক্তি বিশ্লেষণ হচ্ছে' })

    stream.push(
      `id: 1\nevent: warning\ndata: ${JSON.stringify({ code: 'retrieval-keyword-only', message: 'Search used keywords only.' })}\n\n`,
    )

    expect(await screen.findByText('Search used keywords only.')).toBeInTheDocument()
    stream.close()
  })

  it('FR-58 falls back to polling when the stream fails twice, and still finishes', async () => {
    let polls = 0
    server.use(
      http.get(`/api/v1/analyses/${ID}`, () => {
        polls += 1
        return polls < 4 ? running('RISK', 65) : HttpResponse.json(makeResult())
      }),
      http.get(`/api/v1/analyses/${ID}/events`, () => new HttpResponse(null, { status: 503 })),
    )
    page()

    expect(await screen.findByText(/নিয়মিত খবর নেওয়া হচ্ছে/)).toBeInTheDocument()
    expect(await screen.findByText('Dismissal without notice')).toBeInTheDocument()
  })

  it('FR-58 reports a failed analysis in plain words with a way to try again', async () => {
    server.use(
      http.get(`/api/v1/analyses/${ID}`, () =>
        HttpResponse.json({
          analysisId: ID,
          status: 'FAILED',
          stage: 'RISK',
          progress: 100,
          problem: {
            type: 'urn:shromikai:problem:analysis-timeout',
            title: 'x',
            status: 504,
            instance: '/x',
            code: 'analysis-timeout',
            traceId: 't',
          },
        }),
      ),
    )
    page()

    expect(
      await screen.findByRole('heading', { name: 'বিশ্লেষণ সম্পন্ন করা যায়নি' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('বিশ্লেষণে বেশি সময় লেগেছে')
    expect(screen.getByRole('link', { name: 'নতুন করে চেষ্টা করুন' })).toHaveAttribute(
      'href',
      '/app',
    )
  })

  it('FR-54 says an expired result is gone and offers a new analysis', async () => {
    server.use(
      http.get(`/api/v1/analyses/${ID}`, () =>
        problem(410, 'analysis-expired', 'ফলাফলের মেয়াদ শেষ'),
      ),
    )
    page()

    expect(await screen.findByText('ফলাফলটি আর নেই')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'নতুন করে চেষ্টা করুন' })).toBeInTheDocument()
  })
})
