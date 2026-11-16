import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { ReviewItem, UsageReport } from '../../api/admin'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import AdminHome from './AdminHome'
import ApiKeysPage from './ApiKeysPage'
import ReviewPage from './ReviewPage'
import TracePage from './TracePage'
import UsagePage from './UsagePage'

const ID = '77777777-7777-4777-8777-777777777777'

function item(overrides: Partial<ReviewItem> = {}): ReviewItem {
  return {
    id: ID,
    kind: 'FINDING',
    status: 'IN_REVIEW',
    analysisId: null,
    assigneeId: null,
    findingId: 'f9',
    findingKind: 'CONFLICT',
    clauseType: 'PROBATION',
    severity: 'HIGH',
    side: 'WORKER',
    riskSummary: 'The probation looks long.',
    reasonCodes: ['SUPPORT_PARTIAL'],
    facts: [
      {
        parameter: 'PROBATION_LENGTH',
        value: 6,
        unit: 'MONTHS',
        confidence: 0.9,
        regexAgrees: true,
      },
    ],
    candidateSectionKeys: ['XV-LAB:9@original'],
    claimedCitation: {
      sectionKey: 'XV-LAB:9@original',
      quote: 'a probation shall not exceed the stated period',
      claim: 'The probation is longer than the section allows.',
      claimReduced: false,
    },
    corpusChange: null,
    decision: null,
    verification: null,
    createdAt: '2026-11-16T04:00:00Z',
    expiresAt: '2026-12-16T04:00:00Z',
    ...overrides,
  }
}

function reviewPage() {
  return renderApp(
    <Routes>
      <Route path="/admin/review/:id?" element={<ReviewPage />} />
    </Routes>,
    `/admin/review/${ID}`,
  )
}

function listHandler(items: ReviewItem[]) {
  return http.get('/api/v1/review-items', () =>
    HttpResponse.json({ items, page: 0, size: 20, totalItems: items.length }),
  )
}

describe('review queue (FR-60)', () => {
  it('FR-60 FR-73 will not save a decision without a note, and says why', async () => {
    let patched = 0
    server.use(
      listHandler([item()]),
      http.get(`/api/v1/review-items/${ID}`, () => HttpResponse.json(item())),
      http.patch(`/api/v1/review-items/${ID}`, () => {
        patched += 1
        return HttpResponse.json(item({ status: 'REJECTED' }))
      }),
    )
    reviewPage()

    await userEvent.click(await screen.findByRole('radio', { name: 'প্রত্যাখ্যান' }))
    await userEvent.click(screen.getByRole('button', { name: 'সিদ্ধান্ত সংরক্ষণ করুন' }))

    const note = screen.getByLabelText(/মন্তব্য \(আবশ্যক\)/)
    expect(await screen.findByText('সিদ্ধান্তের আগে একটি মন্তব্য লিখুন।')).toBeInTheDocument()
    expect(note).toHaveAttribute('aria-invalid', 'true')
    expect(note).toHaveFocus()
    expect(patched).toBe(0)
  })

  it('FR-60 sends a rejection with its note', async () => {
    let body: unknown = null
    server.use(
      listHandler([item()]),
      http.get(`/api/v1/review-items/${ID}`, () => HttpResponse.json(item())),
      http.patch(`/api/v1/review-items/${ID}`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(
          item({
            status: 'REJECTED',
            decision: {
              decision: 'REJECTED',
              citation: null,
              decidedAt: '2026-11-16T06:00:00Z',
              decidedBy: ID,
              note: 'No section supports it.',
            },
          }),
        )
      }),
    )
    reviewPage()

    await userEvent.click(await screen.findByRole('radio', { name: 'প্রত্যাখ্যান' }))
    await userEvent.type(screen.getByLabelText(/মন্তব্য \(আবশ্যক\)/), 'No section supports it.')
    await userEvent.click(screen.getByRole('button', { name: 'সিদ্ধান্ত সংরক্ষণ করুন' }))

    expect(await screen.findByText('সিদ্ধান্ত সংরক্ষিত হয়েছে।')).toBeInTheDocument()
    expect(body).toEqual({ decision: 'REJECTED', note: 'No section supports it.' })
  })

  it('FR-60 an approval with a citation needs the section, quote and claim and sends all three', async () => {
    let body: unknown = null
    server.use(
      listHandler([item()]),
      http.get(`/api/v1/review-items/${ID}`, () => HttpResponse.json(item())),
      http.patch(`/api/v1/review-items/${ID}`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(item({ status: 'APPROVED' }))
      }),
    )
    reviewPage()

    await userEvent.click(await screen.findByRole('radio', { name: 'উদ্ধৃতিসহ অনুমোদন' }))
    const quote = screen.getByLabelText(/ধারা থেকে নেওয়া হুবহু শব্দ/)
    await userEvent.clear(quote)
    await userEvent.type(quote, 'short')
    await userEvent.type(screen.getByLabelText(/মন্তব্য \(আবশ্যক\)/), 'Checked it.')
    await userEvent.click(screen.getByRole('button', { name: 'সিদ্ধান্ত সংরক্ষণ করুন' }))
    expect(await screen.findByText('কমপক্ষে ২০টি অক্ষর লিখুন।')).toBeInTheDocument()
    expect(body).toBeNull()

    await userEvent.clear(quote)
    await userEvent.type(quote, 'a probation shall not exceed the stated period')
    await userEvent.click(screen.getByRole('button', { name: 'সিদ্ধান্ত সংরক্ষণ করুন' }))

    await waitFor(() => {
      expect(body).toEqual({
        decision: 'APPROVED_WITH_CITATION',
        sectionKey: 'XV-LAB:9@original',
        quote: 'a probation shall not exceed the stated period',
        claim: 'The probation is longer than the section allows.',
        note: 'Checked it.',
      })
    })
  })

  it('FR-60 an open item is taken first; only then can it be decided', async () => {
    let body: unknown = null
    server.use(
      listHandler([item({ status: 'OPEN' })]),
      http.get(`/api/v1/review-items/${ID}`, () => HttpResponse.json(item({ status: 'OPEN' }))),
      http.patch(`/api/v1/review-items/${ID}`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(item())
      }),
    )
    reviewPage()

    expect(await screen.findByText(/আগে আইটেমটি নিন/)).toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'আইটেমটি নিন' }))

    expect(await screen.findByRole('radio', { name: 'প্রত্যাখ্যান' })).toBeInTheDocument()
    expect(body).toEqual({ status: 'IN_REVIEW' })
  })

  it('FR-60 the detail shows redacted facts and reasons in words, never a raw code', async () => {
    server.use(
      listHandler([item()]),
      http.get(`/api/v1/review-items/${ID}`, () => HttpResponse.json(item())),
    )
    reviewPage()

    const panel = await screen.findByRole('region', { name: /শিক্ষানবিশকাল/ })
    expect(within(panel).getByText('The probation looks long.')).toBeInTheDocument()
    expect(within(panel).getByText('শিক্ষানবিশকালের দৈর্ঘ্য')).toBeInTheDocument()
    expect(within(panel).queryByText('SUPPORT_PARTIAL')).not.toBeInTheDocument()
    expect(within(panel).queryByText('PROBATION_LENGTH')).not.toBeInTheDocument()
  })
})

function report(overrides: Partial<UsageReport['kpis']> = {}): UsageReport {
  const day = (date: string, analyses: number) => ({
    date,
    analyses,
    compares: 0,
    memoRenders: 0,
    apiCalls: 0,
    mcpCalls: 0,
    costUsd: analyses * 0.25,
    costByModel: {},
  })
  return {
    from: '2026-11-10',
    to: '2026-11-16',
    orgId: null,
    days: [day('2026-11-15', 1), day('2026-11-16', 3)],
    totals: {
      analyses: 4,
      compares: 0,
      memoRenders: 0,
      apiCalls: 0,
      mcpCalls: 0,
      costUsd: 1,
      costByModel: { 'model-a': 0.75, 'model-b': 0.25 },
    },
    kpis: {
      windowDays: 7,
      completionRate: { value: 0.75, count: 3, n: 4 },
      memoDownloadRate: { value: 1.5, count: 3, n: 2 },
      reviewTurnaroundHoursMedian: { value: 20, n: 2 },
      ...overrides,
    },
  }
}

describe('admin overview and usage (FR-73)', () => {
  it('FR-73 KPI-12 to KPI-14 show the number with the count beside it', async () => {
    server.use(
      http.get('/api/v1/admin/usage', () => HttpResponse.json(report())),
      listHandler([item({ status: 'OPEN' })]),
    )
    renderApp(<AdminHome />, '/admin')

    const values = await screen.findAllByTestId('kpi-value')
    expect(values[0]).toHaveTextContent('৭৫%')
    expect(screen.getByText('৪টির মধ্যে ৩টি')).toBeInTheDocument()
    expect(values[1]).toHaveTextContent('১.৫ গুণ')
    expect(values[2]).toHaveTextContent('২০ ঘণ্টা')
  })

  it('FR-73 R-07 a ratio over no data says there is no data, never zero', async () => {
    server.use(
      http.get('/api/v1/admin/usage', () =>
        HttpResponse.json(
          report({
            completionRate: { value: null, count: 0, n: 0 },
            memoDownloadRate: { value: null, count: 0, n: 0 },
            reviewTurnaroundHoursMedian: { value: null, n: 0 },
          }),
        ),
      ),
      listHandler([]),
    )
    renderApp(<AdminHome />, '/admin')

    const values = await screen.findAllByTestId('kpi-value')
    expect(values.slice(0, 3).map((v) => v.textContent)).toEqual([
      'এই সময়ে কোনো তথ্য নেই',
      'এই সময়ে কোনো তথ্য নেই',
      'এই সময়ে কোনো তথ্য নেই',
    ])
    expect(screen.queryByText(/০%/)).not.toBeInTheDocument()
  })

  it('FR-73 the usage page carries every number in a table, and no chart in low-bandwidth mode', async () => {
    window.localStorage.setItem('shromikai.lowbw', 'true')
    server.use(http.get('/api/v1/admin/usage', () => HttpResponse.json(report())))
    renderApp(<UsagePage />, '/admin/usage')

    const table = await screen.findByRole('table')
    expect(within(table).getAllByRole('row')).toHaveLength(3)
    expect(screen.getByText(/কম ডেটা মোডে চার্ট বন্ধ/)).toBeInTheDocument()
    expect(screen.getByText('model-a')).toBeInTheDocument()
  })
})

describe('API keys (FR-65)', () => {
  it('FR-65 shows the secret once, right after creating the key, and not again', async () => {
    const keys: unknown[] = []
    server.use(
      http.get('/api/v1/admin/api-keys', () => HttpResponse.json(keys)),
      http.post('/api/v1/admin/api-keys', () => {
        const created = {
          id: ID,
          orgId: ID,
          prefix: 'shk_ab12',
          name: 'CI key',
          scopes: ['analyses'],
          key: 'shk_ab12-this-is-the-whole-secret',
          createdAt: '2026-11-16T04:00:00Z',
          lastUsedAt: null,
          revokedAt: null,
        }
        keys.push({ ...created, key: undefined })
        return HttpResponse.json(created, { status: 201 })
      }),
    )
    renderApp(<ApiKeysPage />, '/admin/api-keys')

    await userEvent.type(await screen.findByLabelText(/নাম/), 'CI key')
    await userEvent.click(screen.getByRole('button', { name: 'কী তৈরি করুন' }))

    expect(await screen.findByTestId('api-key-secret')).toHaveTextContent(
      'shk_ab12-this-is-the-whole-secret',
    )
    await userEvent.click(screen.getByRole('button', { name: 'আমি সংরক্ষণ করেছি' }))
    expect(screen.queryByTestId('api-key-secret')).not.toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('this-is-the-whole-secret')
    expect(await screen.findByRole('cell', { name: 'shk_ab12' })).toBeInTheDocument()
  })

  it('FR-65 will not create a key without a name or without any permission', async () => {
    let posted = 0
    server.use(
      http.get('/api/v1/admin/api-keys', () => HttpResponse.json([])),
      http.post('/api/v1/admin/api-keys', () => {
        posted += 1
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    renderApp(<ApiKeysPage />, '/admin/api-keys')

    await userEvent.click(await screen.findByRole('button', { name: 'কী তৈরি করুন' }))

    expect(screen.getByLabelText(/নাম/)).toHaveAttribute('aria-invalid', 'true')
    expect(posted).toBe(0)
  })
})

describe('trace viewer (FR-73)', () => {
  it('FR-73 shows the steps, tokens and cost of one analysis without contract text', async () => {
    server.use(
      http.get(`/api/v1/admin/traces/${ID}`, () =>
        HttpResponse.json({
          analysisId: ID,
          createdAt: '2026-11-16T04:00:00Z',
          events: [
            {
              stage: 'RISK',
              at: '2026-11-16T04:00:01Z',
              durationMs: 1200,
              promptId: 'risk-analysis',
              promptVersion: 2,
              modelId: 'model-a',
              tokensIn: 1000,
              tokensOut: 200,
              cacheReadTokens: null,
              cacheWriteTokens: null,
              costUsdMicros: 250000,
              outcome: 'OK',
              detail: { findings: 3 },
            },
          ],
          totals: {
            tokensIn: 1000,
            tokensOut: 200,
            cacheReadTokens: 0,
            cacheWriteTokens: 0,
            costUsdMicros: 250000,
          },
        }),
      ),
    )
    renderApp(
      <Routes>
        <Route path="/admin/traces/:analysisId?" element={<TracePage />} />
      </Routes>,
      `/admin/traces/${ID}`,
    )

    const table = await screen.findByRole('table')
    expect(within(table).getByText('risk-analysis v2')).toBeInTheDocument()
    expect(within(table).getByText('model-a')).toBeInTheDocument()
    expect(within(table).getByText('১,০০০ / ২০০')).toBeInTheDocument()
  })

  it('FR-73 a missing trace is explained and an invalid ID is refused before any request', async () => {
    server.use(
      http.get(`/api/v1/admin/traces/${ID}`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Not found', status: 404, code: 'not-found' },
          { status: 404 },
        ),
      ),
    )
    renderApp(
      <Routes>
        <Route path="/admin/traces/:analysisId?" element={<TracePage />} />
      </Routes>,
      `/admin/traces/${ID}`,
    )
    expect(await screen.findByText(/কোনো ট্রেস নেই/)).toBeInTheDocument()

    const field = screen.getByLabelText('বিশ্লেষণের আইডি')
    await userEvent.clear(field)
    await userEvent.type(field, 'not-an-id')
    await userEvent.click(screen.getByRole('button', { name: 'ট্রেস দেখান' }))
    expect(await screen.findByText('এটি বিশ্লেষণের আইডি নয়।')).toBeInTheDocument()
  })
})
