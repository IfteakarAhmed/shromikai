import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PreferencesProvider } from '../../state/PreferencesProvider'
import { makeObligation, makeRedline, makeResult } from '../../test/fixtures'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { DutiesView } from './DutiesView'
import { MemoView } from './MemoView'
import { RedlineView } from './RedlineView'
import { ResultsView } from './ResultsView'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  Reflect.deleteProperty(window.navigator, 'share')
  Reflect.deleteProperty(window.navigator, 'clipboard')
})

const noop = () => undefined

describe('RedlineView', () => {
  it('FR-71 shows removed and added words in words as well as marks, under the risk they answer', () => {
    render(
      <PreferencesProvider>
        <RedlineView result={makeResult()} onOpenCitation={noop} />
      </PreferencesProvider>,
    )

    expect(screen.getByText('ঝুঁকি: Dismissal without notice')).toBeInTheDocument()
    const removed = document.querySelector('del')
    expect(removed).toHaveTextContent('at any time without notice')
    expect(within(removed as HTMLElement).getByText(/বাদ যাচ্ছে/)).toBeInTheDocument()
    const added = document.querySelector('ins')
    expect(added).toHaveTextContent('after fourteen days written notice')
    expect(within(added as HTMLElement).getByText(/যোগ হচ্ছে/)).toBeInTheDocument()
  })

  it('FR-71 says so when no rule could check the proposals, and when there are none', () => {
    const { rerender } = render(
      <PreferencesProvider>
        <RedlineView
          result={makeResult({
            warnings: [
              { code: 'counter-offer-unchecked', message: 'No rule checked these proposals.' },
            ],
          })}
          onOpenCitation={noop}
        />
      </PreferencesProvider>,
    )
    expect(
      screen.getByText(
        'কোনো সক্রিয় নিয়ম প্রযোজ্য ছিল না, তাই প্রস্তাবিত পরিবর্তন আইনি সীমার সঙ্গে মেলানো হয়নি।',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByText('আইনের নিয়মের সাথে মিলিয়ে দেখা হয়েছে')).not.toBeInTheDocument()

    rerender(
      <PreferencesProvider>
        <RedlineView
          result={makeResult({
            redlines: [makeRedline({ ruleCheck: { passed: true, ruleIds: ['r'], attempts: 1 } })],
          })}
          onOpenCitation={noop}
        />
      </PreferencesProvider>,
    )
    expect(screen.getByText('আইনের নিয়মের সাথে মিলিয়ে দেখা হয়েছে')).toBeInTheDocument()

    rerender(
      <PreferencesProvider>
        <RedlineView result={makeResult({ redlines: [] })} onOpenCitation={noop} />
      </PreferencesProvider>,
    )
    expect(screen.getByText('কোনো প্রস্তাব নেই।')).toBeInTheDocument()
  })

  it('FR-71 copies the proposed clause', async () => {
    const writeText = vi.fn(() => Promise.resolve())
    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
    render(
      <PreferencesProvider>
        <RedlineView result={makeResult()} onOpenCitation={noop} />
      </PreferencesProvider>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'প্রস্তাবটি কপি করুন' }))

    expect(writeText).toHaveBeenCalledWith(
      'The company may end this contract after fourteen days written notice.',
    )
  })
})

function DutiesHost({ result }: { result: ReturnType<typeof makeResult> }) {
  const [done, setDone] = useState<ReadonlySet<string>>(new Set())
  return (
    <DutiesView
      result={result}
      done={done}
      onToggle={(id) => {
        setDone((current) => {
          const next = new Set(current)
          if (!next.delete(id)) {
            next.add(id)
          }
          return next
        })
      }}
      onOpenCitation={noop}
    />
  )
}

describe('RedlineView across languages', () => {
  const bangla = 'মাসিক বেতন ১১,০০০ টাকা এবং আইনে অনুমোদিত কর্তন ছাড়া অন্য কর্তন হবে না।'

  it('FR-71 shows old and new side by side, each in its own language, when the languages differ', () => {
    render(
      <PreferencesProvider>
        <RedlineView
          result={makeResult({
            redlines: [makeRedline({ proposedText: bangla, language: 'bn' })],
          })}
          onOpenCitation={noop}
        />
      </PreferencesProvider>,
    )

    expect(document.querySelector('del')).toBeNull()
    expect(document.querySelector('ins')).toBeNull()
    const old = screen.getByRole('group', { name: 'আগের লেখা' })
    const added = screen.getByRole('group', { name: 'নতুন প্রস্তাব' })
    expect(old).toHaveTextContent('The company may end this contract at any time without notice.')
    expect(old.querySelector('[lang="en"]')).not.toBeNull()
    expect(added).toHaveTextContent(bangla)
    expect(added.querySelector('[lang="bn"]')).not.toBeNull()
    expect(screen.getByText(/ভাষা আলাদা/)).toBeInTheDocument()
  })

  it('FR-71 keeps the word-level diff when both clauses are in one language', () => {
    render(
      <PreferencesProvider>
        <RedlineView result={makeResult()} onOpenCitation={noop} />
      </PreferencesProvider>,
    )

    expect(document.querySelector('del')).not.toBeNull()
    expect(document.querySelector('ins')).not.toBeNull()
    expect(screen.queryByText(/ভাষা আলাদা/)).not.toBeInTheDocument()
  })

  it('FR-71 a newly added clause with no old text shows only the new one', () => {
    render(
      <PreferencesProvider>
        <RedlineView
          result={makeResult({
            redlines: [
              makeRedline({
                action: 'INSERT',
                clauseId: null,
                originalText: null,
                proposedText: bangla,
                diff: [{ op: 'INSERT', text: bangla }],
                language: 'bn',
              }),
            ],
          })}
          onOpenCitation={noop}
        />
      </PreferencesProvider>,
    )

    expect(screen.queryByRole('group', { name: 'আগের লেখা' })).not.toBeInTheDocument()
  })
})

describe('DutiesView', () => {
  const result = makeResult({
    obligations: [
      makeObligation(),
      makeObligation({ id: 'o2', party: 'WORKER', obligation: 'Work the agreed hours' }),
      makeObligation({ id: 'o3', party: 'BOTH', obligation: 'Keep a written record' }),
    ],
  })

  it("FR-71 lists the reader's own side first, then the other, then both", () => {
    render(
      <PreferencesProvider>
        <DutiesHost result={result} />
      </PreferencesProvider>,
    )

    const headings = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(headings).toEqual(['কর্মীর দায়িত্ব', 'নিয়োগকর্তার দায়িত্ব', 'দুই পক্ষেরই'])
  })

  it('FR-71 lists the employer first for an employer analysis', () => {
    render(
      <PreferencesProvider>
        <DutiesHost result={{ ...result, side: 'EMPLOYER' }} />
      </PreferencesProvider>,
    )

    expect(screen.getAllByRole('heading', { level: 3 })[0]).toHaveTextContent(
      'নিয়োগকর্তার দায়িত্ব',
    )
  })

  it('FR-71 R-02 ticks items, counts them, and stores nothing', async () => {
    render(
      <PreferencesProvider>
        <DutiesHost result={result} />
      </PreferencesProvider>,
    )
    const before = JSON.stringify(Object.entries(window.localStorage))

    await userEvent.click(screen.getByRole('checkbox', { name: /Work the agreed hours/ }))

    expect(screen.getByRole('checkbox', { name: /Work the agreed hours/ })).toBeChecked()
    expect(screen.getByRole('status')).toHaveTextContent('১টি সম্পন্ন, মোট ৩টি')
    expect(JSON.stringify(Object.entries(window.localStorage))).toBe(before)
    expect(window.sessionStorage.length).toBe(0)
  })

  it('FR-71 says plainly when a side has no duties', () => {
    render(
      <PreferencesProvider>
        <DutiesHost result={makeResult({ obligations: [makeObligation()] })} />
      </PreferencesProvider>,
    )

    expect(screen.getAllByText('এই পক্ষের কোনো দায়িত্ব পাওয়া যায়নি।')).toHaveLength(2)
  })
})

describe('MemoView', () => {
  const result = makeResult()

  function memo() {
    return renderApp(<MemoView result={result} />)
  }

  it('FR-71 previews every section of the memo and its disclaimer', () => {
    memo()

    for (const heading of ['Summary', 'Risks', 'Revised clauses', 'Obligations', 'Next steps']) {
      expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
    }
    expect(screen.getAllByText(/This is not legal advice/).length).toBeGreaterThan(0)
    expect(screen.getByRole('table')).toHaveTextContent('Dismissal without notice')
  })

  it('FR-55 downloads the PDF from the server with the signed envelope of the result', async () => {
    let body: { envelope: Record<string, unknown>; format: string } | null = null
    server.use(
      http.post('/api/v1/memos/render', async ({ request }) => {
        body = (await request.json()) as typeof body
        return new HttpResponse(new Uint8Array([37, 80, 68, 70]), {
          headers: { 'Content-Type': 'application/pdf' },
        })
      }),
    )
    const create = vi.fn(() => 'blob:x')
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: create, revokeObjectURL: vi.fn() }))
    memo()

    await userEvent.click(screen.getByRole('button', { name: 'পিডিএফ নামান' }))

    await waitFor(() => {
      expect(create).toHaveBeenCalled()
    })
    const sent = body as unknown as { envelope: Record<string, unknown>; format: string }
    expect(sent.format).toBe('PDF')
    expect(sent.envelope['signature']).toBe(result.envelope.signature)
    expect(sent.envelope['keyId']).toBe('k1')
    expect(sent.envelope['payload']).not.toHaveProperty('envelope')
    expect(sent.envelope['payload']).toHaveProperty('analysisId', result.analysisId)
  })

  it('FR-55 offers printing when the PDF cannot be made', async () => {
    server.use(
      http.post('/api/v1/memos/render', () =>
        HttpResponse.json(
          {
            type: 'urn:shromikai:problem:renderer-unavailable',
            title: 'x',
            status: 503,
            instance: '/x',
            code: 'renderer-unavailable',
            traceId: 't',
          },
          { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    memo()

    await userEvent.click(screen.getByRole('button', { name: 'পিডিএফ নামান' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('প্রিন্ট করে পিডিএফ করে নিতে পারেন')
  })

  it('FR-71 prints with the print stylesheet', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined)
    memo()

    await userEvent.click(screen.getByRole('button', { name: 'প্রিন্ট করুন' }))

    expect(print).toHaveBeenCalledOnce()
    expect(document.querySelector('.memo-print')).not.toBeNull()
  })

  it('FR-71 hides share when the browser has no share API, and shows it when it has', () => {
    const { unmount } = memo()
    expect(screen.queryByRole('button', { name: 'শেয়ার করুন' })).not.toBeInTheDocument()
    unmount()

    Object.defineProperty(window.navigator, 'share', {
      value: vi.fn(() => Promise.resolve()),
      configurable: true,
    })
    memo()

    expect(screen.getByRole('button', { name: 'শেয়ার করুন' })).toBeInTheDocument()
  })

  it('FR-76 hides read-aloud unless the device has a voice for the memo language', () => {
    const { unmount } = memo()
    expect(screen.queryByRole('button', { name: 'শুনুন' })).not.toBeInTheDocument()
    unmount()

    vi.stubGlobal('speechSynthesis', {
      getVoices: () => [{ lang: 'en-GB' }],
      addEventListener: noop,
      removeEventListener: noop,
    })
    const english = renderApp(<MemoView result={result} />)
    expect(screen.queryByRole('button', { name: 'Listen' })).not.toBeInTheDocument()
    english.unmount()

    vi.stubGlobal('speechSynthesis', {
      getVoices: () => [{ lang: 'en-GB' }],
      addEventListener: noop,
      removeEventListener: noop,
    })
    renderApp(<MemoView result={makeResult({ memo: { ...result.memo, language: 'en' } })} />)
    expect(screen.getByRole('button', { name: 'শুনুন' })).toBeInTheDocument()
  })
})

describe('ResultsView tabs', () => {
  function page(route = '/app/analyses/x') {
    return renderApp(<ResultsView result={makeResult()} />, route)
  }

  it('FR-70 FR-71 shows the four parts and opens them by tab', async () => {
    page()

    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent)
    expect(tabs).toEqual(['ঝুঁকি', 'বদলানোর প্রস্তাব', 'কার কী দায়িত্ব', 'মেমো'])
    expect(screen.getByRole('tabpanel', { name: 'ঝুঁকি' })).toBeVisible()

    await userEvent.click(screen.getByRole('tab', { name: 'কার কী দায়িত্ব' }))

    expect(screen.getByRole('tabpanel', { name: 'কার কী দায়িত্ব' })).toBeVisible()
    expect(screen.queryByRole('tabpanel', { name: 'ঝুঁকি' })).not.toBeInTheDocument()
  })

  it('FR-71 moves between tabs with the arrow keys', async () => {
    page()
    screen.getByRole('tab', { name: 'ঝুঁকি' }).focus()

    await userEvent.keyboard('{ArrowRight}')

    expect(screen.getByRole('tab', { name: 'বদলানোর প্রস্তাব' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('NFR-08 swiping a panel moves to the next tab on a touch screen', () => {
    page()
    const panel = screen.getByRole('tabpanel', { name: 'ঝুঁকি' })

    fireEvent.touchStart(panel, { touches: [{ clientX: 300, clientY: 400 }] })
    fireEvent.touchEnd(panel, { changedTouches: [{ clientX: 120, clientY: 410 }] })

    expect(screen.getByRole('tab', { name: 'বদলানোর প্রস্তাব' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('NFR-08 a mostly vertical swipe is a scroll and changes nothing', () => {
    page()
    const panel = screen.getByRole('tabpanel', { name: 'ঝুঁকি' })

    fireEvent.touchStart(panel, { touches: [{ clientX: 200, clientY: 100 }] })
    fireEvent.touchEnd(panel, { changedTouches: [{ clientX: 120, clientY: 500 }] })

    expect(screen.getByRole('tab', { name: 'ঝুঁকি' })).toHaveAttribute('aria-selected', 'true')
  })

  it('FR-71 can be opened straight on a tab from the address', () => {
    page('/app/analyses/x?tab=memo')

    expect(screen.getByRole('tab', { name: 'মেমো' })).toHaveAttribute('aria-selected', 'true')
  })

  it('FR-70 opens the why drawer from a card', async () => {
    server.use(
      http.get('/api/v1/analyses/:id/trace', () =>
        HttpResponse.json({
          analysisId: 'a',
          createdAt: '2026-11-16T10:00:00Z',
          totals: {
            tokensIn: 0,
            tokensOut: 0,
            cacheReadTokens: 0,
            cacheWriteTokens: 0,
            costUsdMicros: 0,
          },
          events: [],
        }),
      ),
    )
    page()

    await userEvent.click(screen.getAllByRole('button', { name: 'কেন?' })[0] as HTMLElement)

    expect(await screen.findByRole('dialog')).toHaveTextContent('কেন এটা ঝুঁকি?')
  })
})
