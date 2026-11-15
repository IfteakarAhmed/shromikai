import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PreferencesProvider } from '../../state/PreferencesProvider'
import { writePreference } from '../../lib/storage'
import { HeroFrame } from './HeroFrame'

const loaded = vi.fn()
vi.mock('./ContractScene', () => {
  loaded()
  return { default: () => <div data-testid="scene" /> }
})

function withWebGl(available: boolean) {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(((kind: string) =>
    available && (kind === 'webgl2' || kind === 'webgl')
      ? { getExtension: () => null }
      : null) as typeof HTMLCanvasElement.prototype.getContext)
}

function withReducedMotion(reduced: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: reduced && query.includes('prefers-reduced-motion'),
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  })) as unknown as typeof window.matchMedia
}

function hero() {
  return render(
    <PreferencesProvider>
      <HeroFrame alt="contract picture" />
    </PreferencesProvider>,
  )
}

beforeEach(() => {
  loaded.mockClear()
  vi.useFakeTimers({ shouldAdvanceTime: true })
})
afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('HeroFrame', () => {
  it('FR-68 always has the poster, with its description, as the base picture', () => {
    withWebGl(false)
    withReducedMotion(false)

    hero()

    expect(screen.getByRole('img', { name: 'contract picture' })).toBeInTheDocument()
  })

  it('FR-68 keeps the poster and never loads the 3D scene when WebGL is missing', async () => {
    withWebGl(false)
    withReducedMotion(false)

    const { container } = hero()
    await act(() => vi.advanceTimersByTimeAsync(2000))

    expect(screen.queryByTestId('scene')).not.toBeInTheDocument()
    expect(container.querySelector('[data-hero]')).toHaveAttribute('data-hero', 'poster')
  })

  it('FR-68 keeps the poster when the visitor prefers reduced motion', async () => {
    withWebGl(true)
    withReducedMotion(true)

    const { container } = hero()
    await act(() => vi.advanceTimersByTimeAsync(2000))

    expect(screen.queryByTestId('scene')).not.toBeInTheDocument()
    expect(container.querySelector('[data-hero]')).toHaveAttribute('data-hero', 'poster')
  })

  it('FR-76 keeps the poster in low-bandwidth mode', async () => {
    writePreference('lowBandwidth', true)
    withWebGl(true)
    withReducedMotion(false)

    const { container } = hero()
    await act(() => vi.advanceTimersByTimeAsync(2000))

    expect(screen.queryByTestId('scene')).not.toBeInTheDocument()
    expect(container.querySelector('[data-hero]')).toHaveAttribute('data-hero', 'poster')
  })

  it('FR-68 shows the 3D scene over the poster once the page is idle, when the browser can', async () => {
    withWebGl(true)
    withReducedMotion(false)

    const { container } = hero()
    expect(screen.queryByTestId('scene')).not.toBeInTheDocument()
    await act(() => vi.advanceTimersByTimeAsync(1000))

    expect(await screen.findByTestId('scene')).toBeInTheDocument()
    expect(container.querySelector('[data-hero]')).toHaveAttribute('data-hero', 'scene')
    expect(screen.getByRole('img', { name: 'contract picture' })).toBeInTheDocument()
  })
})
