import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { writePreference } from '../../lib/storage'
import { PreferencesProvider } from '../../state/PreferencesProvider'
import { GlassCard } from './GlassCard'

function media({ fine = true, reduced = false }: { fine?: boolean; reduced?: boolean }) {
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        matches: query.includes('prefers-reduced-motion')
          ? reduced
          : query.includes('pointer: fine')
            ? fine
            : false,
        media: query,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList,
  )
}

function card(props: Parameters<typeof GlassCard>[0] = {}) {
  return render(
    <PreferencesProvider>
      <GlassCard data-testid="card" {...props}>
        <p>inside</p>
      </GlassCard>
    </PreferencesProvider>,
  )
}

beforeEach(() => {
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
    callback(0)
    return 1
  })
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
    left: 0,
    top: 0,
    right: 300,
    bottom: 200,
    width: 300,
    height: 200,
    x: 0,
    y: 0,
    toJSON: () => ({}),
  })
  Object.defineProperty(HTMLElement.prototype, 'offsetWidth', {
    configurable: true,
    get: () => 300,
  })
  Object.defineProperty(HTMLElement.prototype, 'offsetHeight', {
    configurable: true,
    get: () => 200,
  })
})
afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('GlassCard depth', () => {
  it('FR-68 draws its glass and glow on hidden layers behind the content', () => {
    media({})
    card()
    const element = screen.getByTestId('card')
    const [glass, glow, ...rest] = Array.from(element.children)
    expect(glass).toHaveClass('depth-glass')
    expect(glass).toHaveAttribute('aria-hidden', 'true')
    expect(glow).toHaveClass('depth-glow')
    expect(glow).toHaveAttribute('aria-hidden', 'true')
    expect(rest.map((child) => child.textContent)).toEqual(['inside'])
    expect(element).toHaveClass('depth-card')
  })

  it('FR-68 keeps its props, element type and the reveal', () => {
    media({})
    card({ as: 'article', 'aria-label': 'risk', className: 'p-5' })
    const element = screen.getByRole('article', { name: 'risk' })
    expect(element).toHaveClass('p-5')
    expect(element).toHaveClass('reveal')
  })

  it('FR-68 tilts towards the pointer with a fine pointer and glows where it is', () => {
    media({})
    card()
    const element = screen.getByTestId('card')
    expect(element).toHaveAttribute('data-tilt', 'on')
    fireEvent.pointerMove(element, { clientX: 300, clientY: 100, pointerType: 'mouse' })
    expect(element).toHaveAttribute('data-tilting', 'true')
    expect(element.style.getPropertyValue('--ry')).toBe('6deg')
    expect(Number.parseFloat(element.style.getPropertyValue('--rx'))).toBeCloseTo(0)
    expect(element.style.getPropertyValue('--gx')).toBe('1')
  })

  it('FR-68 settles flat when the pointer leaves', () => {
    media({})
    card()
    const element = screen.getByTestId('card')
    fireEvent.pointerMove(element, { clientX: 300, clientY: 100, pointerType: 'mouse' })
    fireEvent.pointerLeave(element)
    expect(element).not.toHaveAttribute('data-tilting')
    expect(element.style.getPropertyValue('--ry')).toBe('0deg')
    expect(element.style.getPropertyValue('--rx')).toBe('0deg')
  })

  it('FR-68 big documents tilt only a little', () => {
    media({})
    vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(1100)
    card()
    const element = screen.getByTestId('card')
    fireEvent.pointerMove(element, { clientX: 300, clientY: 100, pointerType: 'mouse' })
    expect(Number.parseFloat(element.style.getPropertyValue('--ry'))).toBeLessThan(3)
  })

  it('FR-68 a still card has the same look but never tilts', () => {
    media({})
    card({ depth: 'still' })
    const element = screen.getByTestId('card')
    expect(element.children[0]).toHaveClass('depth-glass')
    expect(element).not.toHaveAttribute('data-tilt')
    fireEvent.pointerMove(element, { clientX: 300, clientY: 100, pointerType: 'mouse' })
    expect(element).not.toHaveAttribute('data-tilting')
  })

  it('NFR-08 does not tilt for a visitor who prefers reduced motion', () => {
    media({ reduced: true })
    card()
    const element = screen.getByTestId('card')
    fireEvent.pointerMove(element, { clientX: 300, clientY: 100, pointerType: 'mouse' })
    expect(element).not.toHaveAttribute('data-tilt')
    expect(element).not.toHaveAttribute('data-tilting')
  })

  it('FR-76 does not tilt in low-bandwidth mode', () => {
    media({})
    writePreference('lowBandwidth', true)
    card()
    const element = screen.getByTestId('card')
    fireEvent.pointerMove(element, { clientX: 300, clientY: 100, pointerType: 'mouse' })
    expect(element).not.toHaveAttribute('data-tilt')
  })

  it('FR-76 does not tilt on a touch screen (no fine pointer)', () => {
    media({ fine: false })
    card()
    const element = screen.getByTestId('card')
    fireEvent.pointerMove(element, { clientX: 300, clientY: 100, pointerType: 'touch' })
    expect(element).not.toHaveAttribute('data-tilt')
    expect(element).not.toHaveAttribute('data-tilting')
  })

  it('NFR-08 a pen or touch pointer on a fine-pointer machine does not start a tilt', () => {
    media({})
    card()
    const element = screen.getByTestId('card')
    act(() => {
      fireEvent.pointerMove(element, { clientX: 300, clientY: 100, pointerType: 'touch' })
    })
    expect(element).not.toHaveAttribute('data-tilting')
  })

  it('NFR-08 holds its lean still while the pointer is over a control, so nothing slides under it', () => {
    media({})
    render(
      <PreferencesProvider>
        <GlassCard data-testid="card">
          <button type="button">act</button>
        </GlassCard>
      </PreferencesProvider>,
    )
    const element = screen.getByTestId('card')
    fireEvent.pointerMove(element, { clientX: 300, clientY: 100, pointerType: 'mouse' })
    expect(element.style.getPropertyValue('--ry')).toBe('6deg')
    fireEvent.pointerMove(screen.getByRole('button', { name: 'act' }), {
      clientX: 0,
      clientY: 200,
      pointerType: 'mouse',
    })
    expect(element.style.getPropertyValue('--ry')).toBe('6deg')
    expect(element.style.getPropertyValue('--rx')).toBe('0deg')
  })
})
