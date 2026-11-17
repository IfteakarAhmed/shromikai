import { act, render, screen } from '@testing-library/react'
import { unstable_HistoryRouter as HistoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createTransitionHistory } from './backForward'

/**
 * Back and Forward run inside a View Transition (C4). The router is told about a POP only from
 * inside the transition's update callback: the browser takes its picture of the old page first,
 * then the page changes, then it takes the new picture.
 */
function withViewTransitions() {
  const callbacks: (() => void)[] = []
  const start = vi.fn((update: () => void) => {
    callbacks.push(update)
    return { finished: Promise.resolve() }
  })
  Object.assign(document, { startViewTransition: start })
  return { start, callbacks }
}

/** What the browser does on Back: the address changes, then `popstate` is sent. */
function back(to: string) {
  window.history.replaceState(null, '', to)
  window.dispatchEvent(new PopStateEvent('popstate', { state: null }))
}

beforeEach(() => {
  window.history.replaceState(null, '', '/first')
})
afterEach(() => {
  vi.restoreAllMocks()
  delete (document as { startViewTransition?: unknown }).startViewTransition
  delete document.documentElement.dataset['lowbw']
})

describe('createTransitionHistory', () => {
  it('FR-68 tells the router about Back only after the browser has its picture of the old page', () => {
    const { start, callbacks } = withViewTransitions()
    const seen: string[] = []
    const history = createTransitionHistory()
    const stop = history.listen((update) => {
      seen.push(`${update.action} ${update.location.pathname}`)
    })

    back('/second')
    expect(start).toHaveBeenCalledTimes(1)
    expect(seen).toEqual([]) // nothing changed yet: the old picture is still being taken

    callbacks[0]?.()
    expect(seen).toEqual(['POP /second'])
    stop()
  })

  it('FR-68 pushes are not wrapped here (link clicks have their own transition)', () => {
    const { start } = withViewTransitions()
    const seen: string[] = []
    const history = createTransitionHistory()
    const stop = history.listen((update) => {
      seen.push(update.action)
    })
    history.push('/third')
    expect(start).not.toHaveBeenCalled()
    expect(seen).toEqual(['PUSH'])
    stop()
  })

  it('FR-68 without View Transitions the router is told at once', () => {
    const seen: string[] = []
    const stop = createTransitionHistory().listen((update) => {
      seen.push(update.location.pathname)
    })
    back('/second')
    expect(seen).toEqual(['/second'])
    stop()
  })

  it('NFR-08 with reduced motion the router is told at once and no transition starts', () => {
    const { start } = withViewTransitions()
    vi.spyOn(window, 'matchMedia').mockImplementation(
      (query) => ({ matches: query.includes('reduce'), media: query }) as MediaQueryList,
    )
    const seen: string[] = []
    const stop = createTransitionHistory().listen((update) => {
      seen.push(update.location.pathname)
    })
    back('/second')
    expect(start).not.toHaveBeenCalled()
    expect(seen).toEqual(['/second'])
    stop()
  })

  it('FR-76 in low-bandwidth mode the router is told at once and no transition starts', () => {
    document.documentElement.dataset['lowbw'] = 'true'
    const { start } = withViewTransitions()
    const seen: string[] = []
    const stop = createTransitionHistory().listen((update) => {
      seen.push(update.location.pathname)
    })
    back('/second')
    expect(start).not.toHaveBeenCalled()
    expect(seen).toEqual(['/second'])
    stop()
  })

  it('FR-68 the page itself changes inside the transition when the app uses this history', () => {
    const { callbacks } = withViewTransitions()
    function Where() {
      return <p data-testid="where">{useLocation().pathname}</p>
    }
    render(
      <HistoryRouter history={createTransitionHistory()} useTransitions={false}>
        <Where />
      </HistoryRouter>,
    )
    expect(screen.getByTestId('where')).toHaveTextContent('/first')

    act(() => {
      back('/second')
    })
    expect(screen.getByTestId('where')).toHaveTextContent('/first')
    act(() => {
      callbacks[0]?.()
    })
    expect(screen.getByTestId('where')).toHaveTextContent('/second')
  })
})
