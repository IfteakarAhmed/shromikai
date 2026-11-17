import { screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/render'

/** The generated report the page reads (R-07); a toy file here, never real numbers. */
const state: { contract: unknown } = { contract: undefined }

vi.mock('./sources', async (original) => {
  const real = await original<typeof import('./sources')>()
  return {
    ...real,
    report: (name: string) => (name === 'report' ? state.contract : real.report(name)),
  }
})

const toy = {
  provenance: {
    gitSha: 'abc1234',
    generatedAt: '2099-01-02T03:04:05Z',
    corpusRelease: '2099.01.01-1',
  },
  config: {
    contracts: ['syn-xx-01', 'syn-xx-02', 'syn-xx-03'],
    labelFiles: 5,
    reviewedLabelFiles: 0,
    adversarialCases: 73,
    liveSpendMicros: 1_234_500,
    verifierIndependence: 'SAME_FAMILY',
    split: 'dev',
  },
  headline: [
    {
      id: 'M-01',
      metric: 'Unverified-citation exposure',
      baseline: '0.600 (6 of 10)',
      shromikai: '0.000 (0 of 21)',
      target: '0',
      met: true,
    },
    {
      id: 'M-04',
      metric: 'Fabricated-citation catch rate',
      baseline: 'not applicable',
      shromikai: '1.000 (55 of 55)',
      target: '0 misses',
      met: true,
    },
  ],
  metrics: {
    shromikai: {
      'M-01': { id: 'M-01', name: 'Unverified-citation exposure', status: 'MEASURED' },
      'M-27': {
        id: 'M-27',
        name: 'Redline acceptance',
        status: 'NOT_AVAILABLE',
        note: 'needs the legal reviewer verdict files',
      },
      'M-21': { id: 'M-21', name: 'Clarity', status: 'NOT_DONE', note: 'judge cut' },
    },
  },
  limitations: ['Small sample: 3 synthetic contracts.'],
  knownFailures: ['A weak citation passed the same-family verifier.'],
}

describe('/docs/evaluation contract numbers (FR-74, R-07)', () => {
  beforeEach(() => {
    state.contract = undefined
  })

  it('FR-74 R-07 says the contract evaluation has not run when there is no report file', async () => {
    const { default: EvaluationPage } = await import('./EvaluationPage')
    renderApp(<EvaluationPage />, '/docs/evaluation')
    expect(screen.getByTestId('contracts-not-run')).toHaveTextContent('এখনো চালানো হয়নি')
    expect(screen.queryByTestId('contracts-headline')).not.toBeInTheDocument()
  })

  it('FR-74 R-07 shows the headline from report.json with counts, sample size, 0 reviewed labels, limitations and what was not measured', async () => {
    state.contract = toy
    const { default: EvaluationPage } = await import('./EvaluationPage')
    renderApp(<EvaluationPage />, '/docs/evaluation')

    expect(screen.queryByTestId('contracts-not-run')).not.toBeInTheDocument()
    const table = screen.getByTestId('contracts-headline')
    const row = within(table).getByRole('row', { name: /M-01/ })
    expect(row).toHaveTextContent('0.000 (0 of 21)')
    expect(row).toHaveTextContent('0.600 (6 of 10)')
    expect(screen.getByTestId('contracts-sample')).toHaveTextContent('৩')
    expect(screen.getByTestId('contracts-sample')).toHaveTextContent('৫টির মধ্যে ০')
    expect(screen.getByText('Small sample: 3 synthetic contracts.')).toBeInTheDocument()
    expect(screen.getByText('A weak citation passed the same-family verifier.')).toBeInTheDocument()
    const missing = screen.getByTestId('contracts-unmeasured')
    expect(missing).toHaveTextContent('M-27')
    expect(missing).toHaveTextContent('needs the legal reviewer verdict files')
    expect(missing).toHaveTextContent('M-21')
    expect(missing).not.toHaveTextContent('M-01')
  })
})
