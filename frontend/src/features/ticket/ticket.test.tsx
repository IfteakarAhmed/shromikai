import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { makeCitation } from '../../test/fixtures'
import { problem } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { renderApp } from '../../test/render'
import { rememberTicketCode } from '../../state/ticketCodes'
import { TicketPage } from './TicketPage'

const ID = '66666666-6666-4666-8666-666666666666'

function page() {
  return renderApp(
    <Routes>
      <Route path="/app/tickets/:ticketId" element={<TicketPage />} />
    </Routes>,
    `/app/tickets/${ID}`,
  )
}

function status(items: unknown[]) {
  return { ticketId: ID, expiresAt: '2026-12-16T10:00:00Z', items }
}

describe('TicketPage', () => {
  it('FR-61 asks for the secret code when this page load does not know it, and sends it in a header', async () => {
    let header: string | null = null
    let url = ''
    server.use(
      http.get(`/api/v1/review-tickets/${ID}`, ({ request }) => {
        header = request.headers.get('X-Ticket-Token')
        url = request.url
        return HttpResponse.json(status([]))
      }),
    )
    page()

    await userEvent.type(screen.getByLabelText('টিকিটের গোপন কোড'), 'my-secret-code')
    await userEvent.click(screen.getByRole('button', { name: 'দেখুন' }))

    expect(await screen.findByText('এই টিকিটে কিছু নেই।')).toBeInTheDocument()
    expect(header).toBe('my-secret-code')
    expect(url).not.toContain('my-secret-code')
  })

  it('FR-61 R-02 uses the code the result showed, without asking, and keeps it out of storage', async () => {
    rememberTicketCode(ID, 'code-from-result')
    server.use(
      http.get(`/api/v1/review-tickets/${ID}`, ({ request }) =>
        request.headers.get('X-Ticket-Token') === 'code-from-result'
          ? HttpResponse.json(
              status([
                {
                  findingId: 'f7',
                  title: 'Resignation needs permission',
                  status: 'OPEN',
                  decision: null,
                  citation: null,
                  note: null,
                  decidedAt: null,
                },
              ]),
            )
          : problem(404, 'not-found', 'x'),
      ),
    )
    page()

    expect(await screen.findByText('Resignation needs permission')).toBeInTheDocument()
    expect(screen.getByText('অপেক্ষায়')).toBeInTheDocument()
    expect(JSON.stringify(Object.entries(window.localStorage))).not.toContain('code-from-result')
    expect(window.sessionStorage.length).toBe(0)
  })

  it('FR-61 shows a decision with its note and, when approved with a section, the verified citation', async () => {
    rememberTicketCode(ID, 'c')
    server.use(
      http.get(`/api/v1/review-tickets/${ID}`, () =>
        HttpResponse.json(
          status([
            {
              findingId: 'f1',
              title: 'First',
              status: 'APPROVED',
              decision: 'APPROVED_WITH_CITATION',
              citation: makeCitation(),
              note: 'Checked against the section.',
              decidedAt: '2026-11-16T12:00:00Z',
            },
            {
              findingId: 'f2',
              title: 'Second',
              status: 'REJECTED',
              decision: 'REJECTED',
              citation: null,
              note: 'Not a real risk.',
              decidedAt: '2026-11-16T12:30:00Z',
            },
          ]),
        ),
      ),
    )
    page()

    const items = await screen.findAllByRole('article')
    expect(within(items[0] as HTMLElement).getByText('অনুমোদিত')).toBeInTheDocument()
    expect(
      within(items[0] as HTMLElement).getByText('Checked against the section.'),
    ).toBeInTheDocument()
    expect(
      within(items[0] as HTMLElement).getByRole('button', { name: /ধারা \S+ খুলুন/ }),
    ).toBeInTheDocument()
    expect(within(items[1] as HTMLElement).getByText('ঝুঁকি হিসেবে বাতিল')).toBeInTheDocument()
    expect(
      within(items[1] as HTMLElement).queryByRole('button', { name: /ধারা/ }),
    ).not.toBeInTheDocument()
  })

  it('FR-61 says the ticket or code did not match, and lets the reader try the code again', async () => {
    rememberTicketCode(ID, 'wrong')
    server.use(http.get(`/api/v1/review-tickets/${ID}`, () => problem(404, 'not-found', 'x')))
    page()

    expect(await screen.findByRole('alert')).toHaveTextContent('টিকিট বা কোডটি মেলেনি।')
    await userEvent.click(screen.getByRole('button', { name: 'টিকিটের গোপন কোড' }))

    expect(screen.getByLabelText('টিকিটের গোপন কোড')).toBeInTheDocument()
  })

  it('FR-61 says an expired ticket has expired', async () => {
    rememberTicketCode(ID, 'c')
    server.use(http.get(`/api/v1/review-tickets/${ID}`, () => problem(410, 'ticket-expired', 'x')))
    page()

    expect(await screen.findByRole('alert')).toHaveTextContent('পর্যালোচনা টিকিটের মেয়াদ শেষ')
  })
})
