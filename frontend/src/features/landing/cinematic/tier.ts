/**
 * Which landing picture a visitor gets (FR-68, FR-76, ADR-0023). Three tiers:
 *
 * - `cinematic`: the full scroll-driven WebGL2 scene, a lazy chunk fetched only for this tier;
 * - `standard`: the smaller framed hero scene;
 * - `lite`: the generated poster, no WebGL at all.
 *
 * Phones, slow connections, data saver, reduced motion and low-bandwidth mode default to lite; the
 * visitor can still opt in to a scene, and can switch back to lite at any time. These are pure
 * functions of the capabilities so every rule is tested.
 */
export type Tier = 'cinematic' | 'standard' | 'lite'

/** The in-memory choice from the landing toggle; it is never stored (R-02, AGENTS.md §6). */
export type SceneChoice = 'auto' | 'lite' | 'scene'

export interface Capabilities {
  webgl: boolean
  webgl2: boolean
  /** `navigator.hardwareConcurrency`; undefined when the browser does not tell. */
  hardwareConcurrency: number | undefined
  /** `navigator.deviceMemory` in GB; only Chromium tells, so undefined counts as enough. */
  deviceMemory: number | undefined
  /** Viewport width in CSS pixels. */
  width: number
  saveData: boolean
  /** `navigator.connection.effectiveType`; undefined when the browser does not tell. */
  effectiveType: string | undefined
  reducedMotion: boolean
  lowBandwidth: boolean
}

const PHONE_WIDTH = 768
const CINEMATIC_WIDTH = 1024
const MIN_CORES = 4
const MIN_MEMORY_GB = 4

/** Hardware that can run the cinematic scene, whatever the visitor's preferences. */
function cinematicHardware(caps: Capabilities): boolean {
  return (
    caps.webgl2 &&
    caps.hardwareConcurrency !== undefined &&
    caps.hardwareConcurrency >= MIN_CORES &&
    (caps.deviceMemory === undefined || caps.deviceMemory >= MIN_MEMORY_GB) &&
    caps.width >= CINEMATIC_WIDTH
  )
}

/** Reasons to start with the poster: the visitor or the connection asks for less. */
function prefersLite(caps: Capabilities): boolean {
  return (
    !caps.webgl ||
    caps.width < PHONE_WIDTH ||
    caps.saveData ||
    (caps.effectiveType !== undefined && caps.effectiveType !== '4g') ||
    caps.reducedMotion ||
    caps.lowBandwidth
  )
}

/** The automatic tier. */
export function selectTier(caps: Capabilities): Tier {
  if (prefersLite(caps)) {
    return 'lite'
  }
  return cinematicHardware(caps) ? 'cinematic' : 'standard'
}

/** The best scene the visitor can turn on by hand, or null when the browser has no WebGL. */
export function optInTier(caps: Capabilities): Exclude<Tier, 'lite'> | null {
  if (!caps.webgl) {
    return null
  }
  return cinematicHardware(caps) ? 'cinematic' : 'standard'
}

/** The tier shown: the automatic one unless the visitor switched. */
export function resolveTier(caps: Capabilities, choice: SceneChoice): Tier {
  switch (choice) {
    case 'lite':
      return 'lite'
    case 'scene':
      return optInTier(caps) ?? 'lite'
    case 'auto':
      return selectTier(caps)
  }
}
