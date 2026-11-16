/**
 * Development-only entry for `scene-render.html`: renders the cinematic scene at a fixed timeline
 * value so `scripts/render-scene.mjs` can make the poster and the social image from the scene
 * itself. It is not part of the production build (Vite builds only `index.html`).
 */
import '../../../index.css'
import { cssColorToHex } from '../oklch'
import { scenePalette } from './palette'
import { createCinematicScene } from './scene'

function token(name: string): string {
  return cssColorToHex(getComputedStyle(document.documentElement).getPropertyValue(name))
}

// ?theme=light renders the dawn scene (the light poster); the default is the night scene.
const scheme =
  new URLSearchParams(window.location.search).get('theme') === 'light' ? 'light' : 'dark'
document.documentElement.dataset['theme'] = scheme

// ?seconds=N sets the scene clock of the still (the shockwave repeats every 2.9 s).
const seconds = Number(new URLSearchParams(window.location.search).get('seconds') ?? 6)

const host = document.getElementById('stage')
if (host === null) {
  throw new Error('no stage')
}
const scene = createCinematicScene(
  host,
  scenePalette(scheme, {
    foreground: token('--foreground'),
    background: token('--background'),
    verified: token('--verified'),
    uncertain: token('--uncertain'),
    rejected: token('--rejected'),
  }),
  { preserveDrawingBuffer: true, autoStart: false },
)

declare global {
  interface Window {
    renderStill: (
      t: number,
      width: number,
      height: number,
      type: string,
      centred?: boolean,
    ) => Promise<string>
  }
}

/** The opening sheets centred, for the poster inside the hero frame (no text beside it there). */
const CENTRED = { position: [2.3, 0.3, 11.5], target: [2.5, 0, 0] } as const

window.renderStill = (t, width, height, type, centred = false) =>
  new Promise((resolve, reject) => {
    const canvas = scene.renderStill(t, width, height, seconds, centred ? CENTRED : undefined)
    canvas.toBlob(
      (blob) => {
        if (blob === null) {
          reject(new Error('no image'))
          return
        }
        const reader = new FileReader()
        reader.onload = () => {
          resolve(typeof reader.result === 'string' ? reader.result : '')
        }
        reader.onerror = () => {
          reject(new Error('read failed'))
        }
        reader.readAsDataURL(blob)
      },
      type,
      0.82,
    )
  })
document.body.dataset['ready'] = 'true'
