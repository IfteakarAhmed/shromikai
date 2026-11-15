import { useState, useSyncExternalStore } from 'react'

function subscribe(query: string, onChange: () => void): () => void {
  const list = window.matchMedia(query)
  list.addEventListener('change', onChange)
  return () => {
    list.removeEventListener('change', onChange)
  }
}

/** Whether a CSS media query matches now, and follows it. */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => subscribe(query, onChange),
    () => window.matchMedia(query).matches,
    () => false,
  )
}

/** Whether this browser can create a WebGL context (checked once). */
export function hasWebGl(): boolean {
  try {
    const canvas = document.createElement('canvas')
    const context = canvas.getContext('webgl2') ?? canvas.getContext('webgl')
    context?.getExtension('WEBGL_lose_context')?.loseContext()
    return context !== null
  } catch {
    return false
  }
}

export function useWebGl(): boolean {
  const [available] = useState(hasWebGl)
  return available
}
