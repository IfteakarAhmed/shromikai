import { screen, within } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import i18n from '../../i18n'
import type { AnalysisResult } from '../../api/analyses'
import { makeFinding, makeResult, makeUncertainFinding } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { ResultsView } from './ResultsView'

const ID = '44444444-4444-4444-8444-444444444444'

function view(result: AnalysisResult) {
  return renderApp(
    <Routes>
      <Route path="/app/analyses/:id" element={<ResultsView result={result} />} />
    </Routes>,
    `/app/analyses/${ID}`,
  )
}

/** True when the element sits inside a closed <details> (hidden until opened). */
const folded = (element: Element) => element.closest('details:not([open])') !== null

beforeEach(async () => {
  await i18n.changeLanguage('bn')
})

describe('ResultsView summary', () => {
  it('FR-70 opens with a plain sentence: the number of big problems, from the API', () => {
    const result = makeResult()
    view({
      ...result,
      summary: {
        ...result.summary,
        findingsBySeverity: { CRITICAL: 4, HIGH: 6, MEDIUM: 3, LOW: 0 },
      },
    })
    expect(
      screen.getByRole('heading', { level: 2, name: 'আপনার চুক্তিতে ১০টি বড় সমস্যা' }),
    ).toBeInTheDocument()
  })

  it('FR-70 says so plainly when there is no big problem', () => {
    const result = makeResult()
    view({
      ...result,
      summary: {
        ...result.summary,
        findingsBySeverity: { CRITICAL: 0, HIGH: 0, MEDIUM: 2, LOW: 0 },
      },
    })
    expect(
      screen.getByRole('heading', {
        level: 2,
        name: 'আপনার চুক্তিতে বড় কোনো সমস্যা পাওয়া যায়নি',
      }),
    ).toBeInTheDocument()
  })

  it('FR-70 এখন কী করবেন links to each part this result has, and nothing else', () => {
    const result = makeResult({ findings: [makeFinding(), makeUncertainFinding()] })
    view(result)
    const steps = screen.getByRole('region', { name: 'এখন কী করবেন' })
    const links = within(steps).getAllByRole('link')
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      `/app/analyses/${ID}?tab=proposals`,
      `/app/tickets/${result.reviewTicket?.ticketId ?? ''}`,
      `/app/analyses/${ID}?tab=duties`,
      `/app/analyses/${ID}?tab=memo`,
    ])
    expect(within(steps).getByText(/এটা আইনি পরামর্শ নয়/)).toBeInTheDocument()
  })

  it('FR-70 leaves out steps the result does not have', () => {
    const result = makeResult({ redlines: [], obligations: [], reviewTicket: null })
    view({ ...result, summary: { ...result.summary, uncertainFindings: 0 } })
    const steps = screen.getByRole('region', { name: 'এখন কী করবেন' })
    expect(within(steps).getAllByRole('link')).toHaveLength(1)
  })

  it('R-12 the not-legal-advice disclaimer stays on the result', () => {
    const result = makeResult()
    view(result)
    expect(screen.getByText(result.disclaimer)).toBeInTheDocument()
  })
})

describe('ResultsView seal', () => {
  it('R-01 shows যাচাই শেষ, with counts from the API, when verification has finished', () => {
    const result = makeResult()
    view(result)
    const seal = screen.getByRole('status', { name: 'যাচাই শেষ' })
    expect(seal).toHaveTextContent('১টি বিষয় যাচাইকৃত, ১টি অনিশ্চিত')
  })

  it('R-01 shows no seal when no verifier ran', () => {
    const result = makeResult()
    view({ ...result, provenance: { ...result.provenance, verifierIndependence: 'NONE' } })
    expect(screen.queryByText('যাচাই শেষ')).not.toBeInTheDocument()
  })

  it('R-01 shows no seal when there was nothing to verify', () => {
    view(makeResult({ findings: [] }))
    expect(screen.queryByText('যাচাই শেষ')).not.toBeInTheDocument()
  })
})

describe('ResultsView cards', () => {
  it('R-01 an uncertain finding stays labelled অনিশ্চিত with the review message in view', () => {
    view(makeResult({ findings: [makeFinding(), makeUncertainFinding()] }))
    const card = screen.getByRole('article', { name: 'Resignation needs permission' })
    expect(folded(within(card).getByText('? অনিশ্চিত'))).toBe(false)
    expect(folded(within(card).getByText('মানুষের পর্যালোচনার জন্য পাঠানো হয়েছে'))).toBe(false)
  })
})
