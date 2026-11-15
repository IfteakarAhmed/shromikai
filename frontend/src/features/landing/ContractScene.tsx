import { useEffect, useRef } from 'react'
import { usePreferences } from '../../state/preferencesContext'
import { createContractScene, type Palette } from './scene3d'

/** A CSS colour token as a hex string (the canvas understands every CSS colour syntax). */
function tokenToHex(name: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  const context = document.createElement('canvas').getContext('2d', { willReadFrequently: true })
  if (context === null) {
    return '#ffffff'
  }
  context.fillStyle = value
  context.fillRect(0, 0, 1, 1)
  const [r = 255, g = 255, b = 255] = context.getImageData(0, 0, 1, 1).data
  return `#${[r, g, b].map((channel) => channel.toString(16).padStart(2, '0')).join('')}`
}

function readPalette(): Palette {
  return {
    paper: tokenToHex('--foreground'),
    verified: tokenToHex('--verified'),
    uncertain: tokenToHex('--uncertain'),
    rejected: tokenToHex('--rejected'),
    ink: tokenToHex('--background'),
  }
}

/**
 * Mounts the 3D scene over the poster and follows the theme. If anything goes wrong while creating
 * it (no GPU, a lost context) it reports the failure and the poster stays (FR-68).
 */
export default function ContractScene({ onFailure }: { onFailure?: () => void }) {
  const host = useRef<HTMLDivElement>(null)
  const { theme } = usePreferences()
  const failure = useRef(onFailure)
  useEffect(() => {
    failure.current = onFailure
  })

  useEffect(() => {
    const element = host.current
    if (element === null) {
      return undefined
    }
    let handle: { dispose: () => void } | null = null
    try {
      // The theme attribute is set synchronously when the theme changes, so these are its colours.
      handle = createContractScene(element, readPalette())
    } catch {
      failure.current?.()
    }
    return () => {
      handle?.dispose()
    }
  }, [theme])

  return <div ref={host} aria-hidden="true" className="absolute inset-0" />
}
