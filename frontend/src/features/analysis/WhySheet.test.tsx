import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import type { RiskFinding } from '../../api/analyses'
import { Button } from '../../components/ui/Button'
import { makeFinding, makeResult, makeUncertainFinding } from '../../test/fixtures'
import { problem } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { WhySheet } from './WhySheet'

const result = makeResult()

function traceBody() {
  const event = (stage: string, detail: Record<string, unknown>) => ({
    stage,
    at: '2026-11-16T10:00:00Z',
    durationMs: 1,
    promptId: null,
    promptVersion: null,
    modelId: null,
    tokensIn: null,
    tokensOut: null,
    cacheReadTokens: null,
    cacheWriteTokens: null,
    costUsdMicros: null,
    outcome: 'OK',
    detail,
  })
  return {
    analysisId: result.analysisId,
    createdAt: '2026-11-16T10:00:00Z',
    totals: {
      tokensIn: 0,
      tokensOut: 0,
      cacheReadTokens: 0,
      cacheWriteTokens: 0,
      costUsdMicros: 0,
    },
    events: [
      event('RETRIEVE', { candidateSections: 40 }),
      event('VERIFY', {
        verdicts: [
          {
            finding: 'f1',
            sectionKey: 'XX-ACT-1:5@original',
            status: 'VERIFIED',
            attempt: 1,
            reasons: [],
          },
          {
            finding: 'f2',
            sectionKey: 'XX-ACT-1:9@original',
            status: 'UNCERTAIN',
            attempt: 1,
            reasons: ['SUPPORT_PARTIAL'],
          },
          {
            finding: 'f2',
            sectionKey: 'XX-ACT-1:8@original',
            status: 'REJECTED',
            attempt: 2,
            reasons: ['QUOTE_NOT_FOUND'],
          },
        ],
      }),
    ],
  }
}

function Host({ finding }: { finding: RiskFinding }) {
  const [open, setOpen] = useState<RiskFinding | null>(null)
  return (
    <>
      <Button
        onClick={() => {
          setOpen(finding)
        }}
      >
        open
      </Button>
      <WhySheet
        finding={open}
        result={result}
        onClose={() => {
          setOpen(null)
        }}
      />
    </>
  )
}

async function openWith(finding: RiskFinding) {
  server.use(
    http.get(`/api/v1/analyses/${result.analysisId}/trace`, () => HttpResponse.json(traceBody())),
  )
  renderApp(<Host finding={finding} />)
  await userEvent.click(screen.getByRole('button', { name: 'open' }))
  return screen.findByRole('dialog')
}

describe('WhySheet', () => {
  it('FR-70 shows the sections retrieved, the three checks and the model that verified', async () => {
    const dialog = await openWith(makeFinding())

    expect(await within(dialog).findByText(/৪০টি প্রাসঙ্গিক ধারা/)).toBeInTheDocument()
    expect(within(dialog).getByText('ধারাটি আছে ও চালু আছে')).toBeInTheDocument()
    expect(within(dialog).getByText('উদ্ধৃত অংশ ধারার সাথে মেলে')).toBeInTheDocument()
    expect(within(dialog).getByText('ধারাটি দাবিকে সমর্থন করে')).toBeInTheDocument()
    expect(within(dialog).getAllByText('পাস')).toHaveLength(3)
    expect(within(dialog).getByText(/যাচাই করেছে: recorded-verifier/)).toBeInTheDocument()
    expect(within(dialog).getByText(/recorded-model/)).toBeInTheDocument()
  })

  it('FR-70 explains an uncertain finding with the sections tried and why each failed', async () => {
    const dialog = await openWith(makeUncertainFinding())

    expect(await within(dialog).findByText('কেন অনিশ্চিত?')).toBeInTheDocument()
    expect(within(dialog).getByText('ধারাটি দাবিকে শুধু আংশিক সমর্থন করে')).toBeInTheDocument()
    expect(within(dialog).getByText('উদ্ধৃত অংশ ধারায় পাওয়া যায়নি')).toBeInTheDocument()
    expect(within(dialog).getAllByText(/চেষ্টা/).length).toBeGreaterThan(0)
    expect(within(dialog).queryByText('ধারাটি আছে ও চালু আছে')).not.toBeInTheDocument()
  })

  it('FR-70 still shows what the result knows when the trace cannot be fetched', async () => {
    server.use(
      http.get(`/api/v1/analyses/${result.analysisId}/trace`, () =>
        problem(503, 'upstream-unavailable', 'x'),
      ),
    )
    renderApp(<Host finding={makeFinding()} />)
    await userEvent.click(screen.getByRole('button', { name: 'open' }))

    const dialog = await screen.findByRole('dialog')

    expect(
      await within(dialog).findByText('যাচাইয়ের বিস্তারিত এখন আনা যায়নি।'),
    ).toBeInTheDocument()
    expect(within(dialog).getAllByText('পাস')).toHaveLength(3)
  })
})
