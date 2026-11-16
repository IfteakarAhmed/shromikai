import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { ALLOWED_STORAGE_KEYS, writePreference } from '../../lib/storage'
import { renderApp } from '../../test/render'
import { LandingPage } from './LandingPage'

const loads = vi.hoisted(() => ({ cinematic: vi.fn(), standard: vi.fn() }))
vi.mock('./cinematic/CinematicScene', () => {
  loads.cinematic()
  return { default: () => <div data-testid="cinematic" /> }
})
vi.mock('./ContractScene', () => {
  loads.standard()
  return { default: () => <div data-testid="scene" /> }
})

const t = (key: string) => i18n.t(key)

function browser({
  webgl = true,
  webgl2 = true,
  width = 1280,
  reducedMotion = false,
}: {
  webgl?: boolean
  webgl2?: boolean
  width?: number
  reducedMotion?: boolean
}) {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(((kind: string) =>
    (kind === 'webgl2' && webgl2) || (kind === 'webgl' && webgl)
      ? { getExtension: () => null }
      : null) as typeof HTMLCanvasElement.prototype.getContext)
  for (const [key, value] of Object.entries({
    hardwareConcurrency: 8,
    deviceMemory: 8,
    connection: { effectiveType: '4g', saveData: false },
  })) {
    Object.defineProperty(navigator, key, { value, configurable: true })
  }
  window.innerWidth = width
  window.matchMedia = ((query: string) => ({
    matches: reducedMotion && query.includes('prefers-reduced-motion'),
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  })) as unknown as typeof window.matchMedia
}

function landing() {
  const view = renderApp(<LandingPage />)
  const root = () => view.container.querySelector('[data-tier]')
  return { ...view, root }
}

beforeEach(() => {
  loads.cinematic.mockClear()
  loads.standard.mockClear()
  vi.useFakeTimers({ shouldAdvanceTime: true })
})
afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('LandingPage tiers', () => {
  it('FR-68 gives a capable desktop the cinematic scene and a visible switch to the still picture', async () => {
    browser({})
    const { root } = landing()

    expect(await screen.findByTestId('cinematic')).toBeInTheDocument()
    expect(root()).toHaveAttribute('data-tier', 'cinematic')
    expect(screen.getByRole('button', { name: t('scene.still') })).toBeVisible()
  })

  it('FR-76 the switch to the still picture removes the scene and shows the poster at once', async () => {
    browser({})
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const { root } = landing()
    await screen.findByTestId('cinematic')

    await user.click(screen.getByRole('button', { name: t('scene.still') }))

    expect(screen.queryByTestId('cinematic')).not.toBeInTheDocument()
    expect(root()).toHaveAttribute('data-tier', 'lite')
    expect(screen.getByRole('img', { name: t('hero.sceneAlt') })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: t('scene.enable') })).toBeVisible()
  })

  it('FR-76 a phone defaults to the poster and can turn the standard scene on', async () => {
    browser({ width: 390 })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const { root } = landing()
    await act(() => vi.advanceTimersByTimeAsync(1500))

    expect(root()).toHaveAttribute('data-tier', 'lite')
    expect(loads.cinematic).not.toHaveBeenCalled()
    expect(screen.queryByTestId('scene')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: t('scene.enable') }))
    await act(() => vi.advanceTimersByTimeAsync(1000))

    expect(root()).toHaveAttribute('data-tier', 'standard')
    expect(await screen.findByTestId('scene')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: t('scene.still') })).toBeVisible()
  })

  it('FR-76 reduced motion defaults to the poster and never fetches the cinematic chunk', async () => {
    browser({ reducedMotion: true })
    const { root } = landing()
    await act(() => vi.advanceTimersByTimeAsync(1500))

    expect(root()).toHaveAttribute('data-tier', 'lite')
    expect(screen.queryByTestId('cinematic')).not.toBeInTheDocument()
    expect(screen.queryByTestId('scene')).not.toBeInTheDocument()
    expect(loads.cinematic).not.toHaveBeenCalled()
  })

  it('FR-76 low-bandwidth mode defaults to the poster and never fetches the cinematic chunk', async () => {
    writePreference('lowBandwidth', true)
    browser({})
    const { root } = landing()
    await act(() => vi.advanceTimersByTimeAsync(1500))

    expect(root()).toHaveAttribute('data-tier', 'lite')
    expect(loads.cinematic).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: t('scene.enable') })).toBeVisible()
  })

  it('FR-68 offers no scene switch when the browser has no WebGL', async () => {
    browser({ webgl: false, webgl2: false })
    const { root } = landing()
    await act(() => vi.advanceTimersByTimeAsync(1500))

    expect(root()).toHaveAttribute('data-tier', 'lite')
    expect(screen.queryByRole('button', { name: t('scene.enable') })).not.toBeInTheDocument()
  })

  it('R-02 switching the scene stores nothing beyond the allowed preferences', async () => {
    browser({})
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    landing()
    await screen.findByTestId('cinematic')

    await user.click(screen.getByRole('button', { name: t('scene.still') }))
    await user.click(screen.getByRole('button', { name: t('scene.enable') }))

    for (const key of Object.keys(window.localStorage)) {
      expect(ALLOWED_STORAGE_KEYS).toContain(key)
    }
    expect(window.sessionStorage.length).toBe(0)
    expect(window.localStorage.getItem('shromikai.lowbw')).toBeNull()
  })
})

describe('LandingPage story', () => {
  it('FR-68 tells the story in six chapters, in order, in every tier', () => {
    for (const width of [1280, 390]) {
      browser({ width })
      const { container, unmount } = landing()
      const chapters = [...container.querySelectorAll('[data-chapter]')].map((element) =>
        element.getAttribute('data-chapter'),
      )
      expect(chapters).toEqual(['hero', 'privacy', 'search', 'verify', 'invented', 'finale'])
      expect(screen.getByRole('heading', { level: 1, name: t('hero.title') })).toBeInTheDocument()
      unmount()
    }
  })

  it('R-07 the invented-law chapter says it is an illustration and shows no numbers', () => {
    browser({})
    const { container } = landing()
    const chapter = container.querySelector<HTMLElement>('[data-chapter="invented"]')
    if (chapter === null) {
      throw new Error('no invented-law chapter')
    }

    expect(within(chapter).getByText(t('story.illustration'))).toBeInTheDocument()
    expect(chapter.textContent).not.toMatch(/[0-9০-৯]/)
  })

  it('NFR-09 keeps the privacy promise on the landing', () => {
    browser({ width: 390 })
    landing()
    expect(screen.getAllByText(t('trust.privateStored')).length).toBeGreaterThan(0)
  })
})
