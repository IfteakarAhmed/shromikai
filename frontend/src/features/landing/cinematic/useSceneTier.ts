import { useCallback, useState, useSyncExternalStore } from 'react'
import { useSearchParams } from 'react-router-dom'
import { usePreferences } from '../../../state/preferencesContext'
import { useMediaQuery } from '../useCapabilities'
import { readCapabilities } from './capabilities'
import { optInTier, resolveTier, type SceneChoice, type Tier } from './tier'

function subscribeWidth(onChange: () => void): () => void {
  window.addEventListener('resize', onChange)
  return () => {
    window.removeEventListener('resize', onChange)
  }
}

const FORCED: readonly SceneChoice[] = ['lite', 'standard', 'cinematic']

export interface SceneTier {
  tier: Tier
  /** The scene the visitor could turn on, or null without WebGL (then there is no switch). */
  optIn: Exclude<Tier, 'lite'> | null
  /** Switch to the still picture or turn the scene on; kept in memory only (R-02). */
  choose: (choice: 'lite' | 'scene') => void
  /** The scene failed (no GPU, lost context): the poster stays for this page. */
  fail: () => void
}

/**
 * The landing tier (FR-68, FR-76): read the browser once, follow the window width, the reduced-
 * motion setting and low-bandwidth mode, and the visitor's switch. `?scene=lite|standard|cinematic`
 * forces a tier for review and screenshots; nothing is written to storage.
 */
export function useSceneTier(): SceneTier {
  const { lowBandwidth } = usePreferences()
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  const width = useSyncExternalStore(
    subscribeWidth,
    () => window.innerWidth,
    () => 1024,
  )
  const [base] = useState(() => readCapabilities({ reducedMotion: false, lowBandwidth: false }))
  const [params] = useSearchParams()
  const requested = params.get('scene') as SceneChoice | null
  const [choice, setChoice] = useState<SceneChoice>(
    requested !== null && FORCED.includes(requested) ? requested : 'auto',
  )
  const [failed, setFailed] = useState(false)
  const caps = { ...base, width, reducedMotion, lowBandwidth }
  const choose = useCallback((next: 'lite' | 'scene') => {
    setChoice(next)
  }, [])
  const fail = useCallback(() => {
    setFailed(true)
  }, [])
  return {
    tier: failed ? 'lite' : resolveTier(caps, choice),
    optIn: failed ? null : optInTier(caps),
    choose,
    fail,
  }
}
