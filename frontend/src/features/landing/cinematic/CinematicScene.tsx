import { useEffect, useRef } from 'react'
import { cssColorToHex } from '../oklch'
import type { Palette } from '../scene3d'
import { parseDevFlags } from './devFlags'
import { createCinematicScene, type CinematicHandle } from './scene'
import { timelineFromScroll } from './timeline'

function readPalette(element: HTMLElement): Palette {
  const style = getComputedStyle(element)
  const token = (name: string) => cssColorToHex(style.getPropertyValue(name))
  return {
    paper: token('--foreground'),
    verified: token('--verified'),
    uncertain: token('--uncertain'),
    rejected: token('--rejected'),
    ink: token('--background'),
  }
}

/**
 * The fixed full-screen canvas of the cinematic landing (a lazy chunk, ADR-0023). Native scrolling
 * drives it: the chapter sections (`[data-chapter]`) are measured, and the middle of the viewport
 * among them is the timeline value. It runs only while the story is on screen and the tab is
 * visible. It is decoration (`aria-hidden`); the chapters in HTML carry the meaning.
 */
export default function CinematicScene({ onFailure }: { onFailure: () => void }) {
  const host = useRef<HTMLDivElement>(null)
  const failure = useRef(onFailure)
  useEffect(() => {
    failure.current = onFailure
  })

  useEffect(() => {
    const element = host.current
    if (element === null) {
      return undefined
    }
    let handle: CinematicHandle
    try {
      handle = createCinematicScene(element, readPalette(element), {
        onContextLost: () => {
          failure.current()
        },
        flags: parseDevFlags(window.location.search, import.meta.env.DEV),
      })
    } catch {
      failure.current()
      return undefined
    }

    let starts: number[] = []
    let end = 0
    function measure() {
      const sections = [...document.querySelectorAll<HTMLElement>('[data-chapter]')]
      starts = sections.map((section) => section.getBoundingClientRect().top + window.scrollY)
      const last = sections[sections.length - 1]
      end = last === undefined ? 0 : last.getBoundingClientRect().bottom + window.scrollY
    }
    function onScroll() {
      handle.setTarget(timelineFromScroll(window.scrollY + window.innerHeight * 0.5, starts, end))
    }
    function onPointer(event: PointerEvent) {
      handle.setPointer(
        (event.clientX / window.innerWidth) * 2 - 1,
        -((event.clientY / window.innerHeight) * 2 - 1),
      )
    }
    measure()
    onScroll()
    const layout = new ResizeObserver(() => {
      measure()
      onScroll()
    })
    layout.observe(document.body)
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('pointermove', onPointer, { passive: true })

    // Pause when the tab is hidden or the story has scrolled out of view.
    let storyVisible = true
    const sync = () => {
      handle.setRunning(storyVisible && document.visibilityState === 'visible')
    }
    const story = document.querySelector('[data-story]')
    const seen = new IntersectionObserver((entries) => {
      storyVisible = entries.some((entry) => entry.isIntersecting)
      sync()
    })
    if (story !== null) {
      seen.observe(story)
    }
    document.addEventListener('visibilitychange', sync)

    return () => {
      layout.disconnect()
      seen.disconnect()
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('pointermove', onPointer)
      document.removeEventListener('visibilitychange', sync)
      handle.dispose()
    }
  }, [])

  return (
    <div
      ref={host}
      aria-hidden="true"
      data-scene="cinematic"
      className="pointer-events-none fixed inset-0 z-0"
    />
  )
}
