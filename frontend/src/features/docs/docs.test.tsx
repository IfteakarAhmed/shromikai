import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { sampleMeta } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { formatNumber } from '../../lib/format'
import ArchitecturePage from './ArchitecturePage'
import AuditPage from './AuditPage'
import DataPage from './DataPage'
import DocsIndex from './DocsIndex'
import EvaluationPage from './EvaluationPage'
import PromptsPage from './PromptsPage'
import SecurityPage from './SecurityPage'
import { corpusQuality, retrievalReport } from './reports'

describe('docs hub (FR-74)', () => {
  it('FR-74 /docs/audit shows the values of the live /meta response', async () => {
    server.use(
      http.get('/api/v1/meta', () =>
        HttpResponse.json({
          ...sampleMeta,
          version: '9.8.7',
          corpus: {
            release: '2099.01.01-7',
            acts: 3,
            sections: 456,
            currentSections: 400,
            embeddedSections: 399,
            lastRefreshAt: null,
          },
          audit: { privacyBlocksLast7Days: 11, openReviewItems: 5, latestEval: null },
        }),
      ),
    )
    renderApp(<AuditPage />, '/docs/audit')

    expect(await screen.findByTestId('audit-version')).toHaveTextContent('৯.৮.৭')
    expect(screen.getByTestId('audit-release')).toHaveTextContent('2099.01.01-7')
    expect(screen.getByTestId('audit-sections')).toHaveTextContent('৪৫৬')
    expect(screen.getByTestId('audit-embedded')).toHaveTextContent('৩৯৯')
    expect(screen.getByTestId('audit-privacy-blocks')).toHaveTextContent('১১')
    expect(screen.getByTestId('audit-open-reviews')).toHaveTextContent('৫')
    // R-07: with no contract evaluation the page says so and shows no number for it
    expect(screen.getByText(/চুক্তির মূল্যায়ন এখনো চালানো হয়নি/)).toBeInTheDocument()
  })

  it('FR-74 /docs/audit shows the latest evaluation only when the system reports one', async () => {
    server.use(
      http.get('/api/v1/meta', () =>
        HttpResponse.json({
          ...sampleMeta,
          audit: {
            privacyBlocksLast7Days: 0,
            openReviewItems: 0,
            latestEval: {
              cases: 12,
              riskRecall: 0.5,
              citationPrecision: 0.25,
              fabricatedCitationsShown: 0,
              reportDate: '2099-01-01',
            },
          },
        }),
      ),
    )
    renderApp(<AuditPage />, '/docs/audit')

    expect(await screen.findByText('৫০%')).toBeInTheDocument()
    expect(screen.getByText('২৫%')).toBeInTheDocument()
    expect(screen.queryByText(/চুক্তির মূল্যায়ন এখনো চালানো হয়নি/)).not.toBeInTheDocument()
  })

  it('FR-74 R-07 /docs/evaluation takes the retrieval numbers from the report file, with counts, and says the contract evaluation has not run', () => {
    const report = retrievalReport()
    expect(report).not.toBeNull()
    renderApp(<EvaluationPage />, '/docs/evaluation')

    const table = screen.getByRole('table')
    const row = report?.rows.find((r) => r.method === 'HYBRID_GRAPH')
    expect(row).toBeDefined()
    const cells = within(
      within(table).getByRole('row', { name: /দুটো, সঙ্গে যুক্ত ধারা/ }),
    ).getAllByRole('cell')
    expect(cells[0]).toHaveTextContent(
      `${formatNumber(row?.questions ?? 0, 'bn')}-এর মধ্যে ${formatNumber(row?.hitsAt5 ?? 0, 'bn')}`,
    )
    expect(screen.getByTestId('contracts-not-run')).toHaveTextContent('এখনো চালানো হয়নি')
    // a method that was not run is shown as not run, with the file's own reason, not as a number
    expect(screen.getAllByText('চালানো হয়নি').length).toBeGreaterThan(0)
  })

  it('FR-74 /docs/data lists the acts of the corpus quality report and its provenance', () => {
    const quality = corpusQuality()
    expect(quality).not.toBeNull()
    renderApp(<DataPage />, '/docs/data')

    for (const act of quality?.acts ?? []) {
      expect(screen.getAllByText(act.code).length).toBeGreaterThan(0)
    }
    expect(screen.getByText(/টিউনিং|আইন-সংগ্রহের সংস্করণ/)).toBeInTheDocument()
  })

  it('FR-74 /docs/architecture draws both pictures as named images, not as a diagram library', () => {
    renderApp(<ArchitecturePage />, '/docs/architecture')

    expect(screen.getByRole('img', { name: 'অংশগুলো কোথায় চলে' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'একটি বিশ্লেষণের ধাপ' })).toBeInTheDocument()
    const ids = [...document.querySelectorAll('[id]')].map((el) => el.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('FR-74 /docs/security shows the threat model from the specification', async () => {
    renderApp(<SecurityPage />, '/docs/security')

    expect(await screen.findByRole('heading', { name: /Threat model/ })).toBeInTheDocument()
    expect(screen.getByText('চুক্তির কোনো লেখা জমা থাকে না')).toBeInTheDocument()
  })

  it('FR-74 /docs/prompts lists every prompt and opens its page', async () => {
    renderApp(
      <Routes>
        <Route path="/docs/prompts/:id?" element={<PromptsPage />} />
      </Routes>,
      '/docs/prompts',
    )

    await userEvent.click(await screen.findByRole('link', { name: 'risk-analysis' }))

    expect(
      await screen.findByRole('heading', { name: 'risk-analysis', level: 1 }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'সব প্রম্পট' })).toBeInTheDocument()
  })

  it('FR-74 /docs links to every page of the hub', () => {
    renderApp(<DocsIndex />, '/docs')

    for (const name of [
      'স্থাপত্য',
      'তথ্য',
      'গোপনীয়তা ও নিরাপত্তা',
      'সরাসরি নিরীক্ষা',
      'মূল্যায়ন',
      'প্রম্পট',
      'জানা সীমাবদ্ধতা',
    ]) {
      expect(screen.getByRole('link', { name })).toBeInTheDocument()
    }
  })
})
