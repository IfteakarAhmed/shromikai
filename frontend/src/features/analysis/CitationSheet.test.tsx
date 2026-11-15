import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import type { VerifiedCitation } from '../../api/analyses'
import { Button } from '../../components/ui/Button'
import { QUOTE, makeCitation } from '../../test/fixtures'
import { problem } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { groupCitations, type CitationGroup } from '../../lib/citations'
import { CitationSheet } from './CitationSheet'

const TEXT = `1. General. ${QUOTE} Other duties are in section 6.`

function section(overrides: Record<string, unknown> = {}) {
  return {
    sectionKey: 'XX-ACT-1:5@original',
    actCode: 'XX-ACT-1',
    actTitleBn: 'কাল্পনিক আইন (শুধু পরীক্ষার জন্য)',
    actTitleEn: 'Fictional Act (test only)',
    sectionNo: '5',
    sectionNoOriginal: '5',
    headingBn: 'নোটিশ',
    headingEn: 'Notice',
    authoritativeLang: 'en',
    authoritativeText: TEXT,
    translation: null,
    versionLabel: 'original',
    effectiveFrom: null,
    isCurrent: true,
    sourceUrl: 'https://example.invalid/xx/5',
    fetchedAt: '2026-11-01T00:00:00Z',
    contentHash: 'h',
    related: [],
    corpusRelease: 'xx',
    ...overrides,
  }
}

function Host({ citations = [makeCitation()] }: { citations?: VerifiedCitation[] }) {
  const [open, setOpen] = useState<CitationGroup | null>(null)
  return (
    <>
      <Button
        onClick={() => {
          setOpen(groupCitations(citations)[0] ?? null)
        }}
      >
        open
      </Button>
      <CitationSheet
        group={open}
        onClose={() => {
          setOpen(null)
        }}
      />
    </>
  )
}

describe('CitationSheet', () => {
  it('FR-70 shows the full section text with the quoted span highlighted and the section facts', async () => {
    server.use(http.get('/api/v1/laws/XX-ACT-1/5', () => HttpResponse.json(section())))
    renderApp(<Host />)

    await userEvent.click(screen.getByRole('button', { name: 'open' }))

    const dialog = await screen.findByRole('dialog')
    const mark = await within(dialog).findByText(QUOTE)
    expect(mark.tagName).toBe('MARK')
    expect(dialog).toHaveTextContent('Other duties are in section 6.')
    expect(dialog).toHaveTextContent('কাল্পনিক আইন')
    expect(dialog).toHaveTextContent('original')
  })

  it('FR-70 links to the official source in a new tab, safely', async () => {
    server.use(http.get('/api/v1/laws/XX-ACT-1/5', () => HttpResponse.json(section())))
    renderApp(<Host />)
    await userEvent.click(screen.getByRole('button', { name: 'open' }))

    const link = await screen.findByRole('link', { name: /সরকারি উৎসে দেখুন/ })

    expect(link).toHaveAttribute('href', 'https://example.invalid/xx/5')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('FR-70 refuses a link that is not http or https', async () => {
    server.use(
      http.get('/api/v1/laws/XX-ACT-1/5', () =>
        HttpResponse.json(section({ sourceUrl: 'javascript:alert(1)' })),
      ),
    )
    renderApp(<Host citations={[makeCitation({ sourceUrl: 'javascript:alert(1)' })]} />)
    await userEvent.click(screen.getByRole('button', { name: 'open' }))

    await screen.findByText(QUOTE)

    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it('FR-70 R-01 shows the section without a highlight when the quote is not in it', async () => {
    server.use(
      http.get('/api/v1/laws/XX-ACT-1/5', () =>
        HttpResponse.json(section({ authoritativeText: 'A different section text entirely.' })),
      ),
    )
    renderApp(<Host />)
    await userEvent.click(screen.getByRole('button', { name: 'open' }))

    expect(await screen.findByText('A different section text entirely.')).toBeInTheDocument()
    const dialog = screen.getByRole('dialog')
    expect(dialog.querySelector('mark')).toBeNull()
    expect(within(dialog).getByText(/আলাদা করে চিহ্নিত করা যায়নি/)).toBeInTheDocument()
  })

  it('FR-70 shows only the verified quote when the section cannot be fetched', async () => {
    server.use(http.get('/api/v1/laws/XX-ACT-1/5', () => problem(503, 'upstream-unavailable', 'x')))
    renderApp(<Host />)
    await userEvent.click(screen.getByRole('button', { name: 'open' }))

    const mark = await screen.findByText(QUOTE)

    expect(mark.tagName).toBe('MARK')
    expect(screen.getByText(/পূর্ণ পাঠ এখন আনা যায়নি/)).toBeInTheDocument()
  })

  it('FR-70 closes with the close button and returns focus to the control that opened it', async () => {
    server.use(http.get('/api/v1/laws/XX-ACT-1/5', () => HttpResponse.json(section())))
    renderApp(<Host />)
    const opener = screen.getByRole('button', { name: 'open' })
    await userEvent.click(opener)
    await screen.findByRole('dialog')

    await userEvent.click(screen.getByRole('button', { name: 'বন্ধ করুন' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('FR-70 marks both passages when a finding cites two passages of the section', async () => {
    const other = 'Other duties are in section 6.'
    server.use(http.get('/api/v1/laws/XX-ACT-1/5', () => HttpResponse.json(section())))
    renderApp(
      <Host
        citations={[
          makeCitation({ citationId: 'a' }),
          makeCitation({ citationId: 'b', quote: other }),
        ]}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: 'open' }))

    const dialog = await screen.findByRole('dialog')
    await within(dialog).findByText(QUOTE)

    expect(Array.from(dialog.querySelectorAll('mark')).map((m) => m.textContent)).toEqual([
      QUOTE,
      other,
    ])
  })
})
