import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { writePreference } from '../../lib/storage'
import { PreferencesProvider } from '../../state/PreferencesProvider'
import { Badge } from './Badge'
import { LinkButton } from './Button'
import { buttonClass } from './buttonClass'
import { GlassCard } from './GlassCard'

afterEach(() => {
  vi.restoreAllMocks()
  delete (document as { startViewTransition?: unknown }).startViewTransition
})

describe('buttonClass', () => {
  it('NFR-08 gives phones 56 px touch targets and keeps 44 px from tablet width up', () => {
    expect(buttonClass('primary', 'md')).toMatch(/\bbtn\b/)
    expect(buttonClass('primary', 'md')).toMatch(/\bmin-h-14\b/)
    expect(buttonClass('primary', 'md')).toMatch(/\bmd:min-h-11\b/)
    expect(buttonClass('primary', 'lg')).toMatch(/\bmin-h-14\b/)
    expect(buttonClass('outline', 'icon')).toMatch(/\bsize-14\b/)
    expect(buttonClass('outline', 'icon')).toMatch(/\bmd:size-11\b/)
  })
})

describe('Badge and GlassCard', () => {
  it('NFR-09 sets badge labels in the interface face', () => {
    render(<Badge>label</Badge>)
    expect(screen.getByText('label')).toHaveClass('font-ui')
  })

  it('NFR-08 cards reveal on scroll by default and can opt out', () => {
    const { container, rerender } = render(<GlassCard>card</GlassCard>)
    expect(container.firstElementChild).toHaveClass('reveal')
    rerender(<GlassCard reveal={false}>card</GlassCard>)
    expect(container.firstElementChild).not.toHaveClass('reveal')
  })
})

function app() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <PreferencesProvider>
        <Routes>
          <Route path="/" element={<LinkButton to="/next">go</LinkButton>} />
          <Route path="/next" element={<p>arrived</p>} />
        </Routes>
      </PreferencesProvider>
    </MemoryRouter>,
  )
}

function withViewTransitions() {
  const start = vi.fn((update: () => void) => {
    update()
    return { finished: Promise.resolve() }
  })
  Object.assign(document, { startViewTransition: start })
  return start
}

describe('LinkButton route transitions', () => {
  it('NFR-08 moves to the next page inside a view transition when the browser has them', () => {
    const start = withViewTransitions()
    app()
    fireEvent.click(screen.getByRole('link', { name: 'go' }))
    expect(start).toHaveBeenCalledTimes(1)
    expect(screen.getByText('arrived')).toBeInTheDocument()
  })

  it('FR-76 navigates without a transition in low-bandwidth mode', () => {
    writePreference('lowBandwidth', true)
    const start = withViewTransitions()
    app()
    fireEvent.click(screen.getByRole('link', { name: 'go' }))
    expect(start).not.toHaveBeenCalled()
    expect(screen.getByText('arrived')).toBeInTheDocument()
  })

  it('NFR-08 navigates without a transition when the visitor prefers reduced motion', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(
      (query: string) =>
        ({
          matches: query.includes('prefers-reduced-motion'),
          media: query,
          addEventListener: () => undefined,
          removeEventListener: () => undefined,
        }) as unknown as MediaQueryList,
    )
    const start = withViewTransitions()
    app()
    fireEvent.click(screen.getByRole('link', { name: 'go' }))
    expect(start).not.toHaveBeenCalled()
    expect(screen.getByText('arrived')).toBeInTheDocument()
  })

  it('NFR-08 leaves modified clicks (new tab) to the browser', () => {
    const start = withViewTransitions()
    app()
    fireEvent.click(screen.getByRole('link', { name: 'go' }), { ctrlKey: true })
    expect(start).not.toHaveBeenCalled()
  })
})
