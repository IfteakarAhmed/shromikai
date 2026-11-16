import { useEffect, useRef } from 'react'
import { usePreferences } from '../../state/preferencesContext'
import { cssColorToHex } from './oklch'
import { createContractScene, type Palette } from './scene3d'

function tokenToHex(name: string): string {
  return cssColorToHex(getComputedStyle(document.documentElement).getPropertyValue(name))
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
