// Renders stills of the cinematic landing scene with headless Chromium and SwiftShader (software
// WebGL), so the lite poster and the social preview image come from the scene itself (ADR-0023).
//
//   node scripts/render-scene.mjs                 # poster + og image into the repo
//   node scripts/render-scene.mjs --frames DIR    # timeline frames 0, 25, 50, 75, 100 % into DIR
//   node scripts/render-scene.mjs --frames DIR --at 41.7,58.3   # other timeline values
//
// It starts its own Vite dev server on a free port; it needs no backend.
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from '@playwright/test'
import { createServer } from 'vite'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const framesAt = process.argv.indexOf('--frames')
const framesDir = framesAt > 0 ? resolve(process.argv[framesAt + 1] ?? '.') : null
// Optional timeline values for --frames, as percentages: --at 0,25,41.7
const atIndex = process.argv.indexOf('--at')
const percents =
  atIndex > 0 ? (process.argv[atIndex + 1] ?? '').split(',').map(Number) : [0, 25, 50, 75, 100]

const server = await createServer({
  root,
  logLevel: 'error',
  server: { port: 0, strictPort: false, hmr: false },
})
await server.listen()
const address = server.httpServer?.address()
const port = typeof address === 'object' && address !== null ? address.port : 0
const browser = await chromium.launch({
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
})

async function render(t, width, height, type, centred = false) {
  const page = await browser.newPage({ viewport: { width, height } })
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  await page.goto(`http://localhost:${port}/scene-render.html`)
  await page.waitForSelector('body[data-ready="true"]', { state: 'attached', timeout: 60_000 })
  const dataUrl = await page.evaluate(
    ([at, w, h, kind, centre]) => globalThis.renderStill(at, w, h, kind, centre),
    [t, width, height, type, centred],
  )
  await page.close()
  if (errors.length > 0) {
    throw new Error(`render errors: ${errors.join(' | ')}`)
  }
  return Buffer.from(dataUrl.split(',')[1], 'base64')
}

function save(path, bytes) {
  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(path, bytes)
  console.log(`${path}: ${(bytes.length / 1024).toFixed(1)} KB`)
}

try {
  if (framesDir !== null) {
    for (const percent of percents) {
      save(
        resolve(framesDir, `scene-${percent}.png`),
        await render(percent / 100, 1280, 800, 'image/png'),
      )
    }
  } else {
    // The lite poster: the opening composition centred, in the hero frame's 5:4 shape.
    save(
      resolve(root, 'src/assets/contract-poster.webp'),
      await render(0, 960, 768, 'image/webp', true),
    )
    // The social preview (1200 × 630): the closing pull-back.
    save(resolve(root, 'public/og-image.jpg'), await render(1, 1200, 630, 'image/jpeg'))
  }
} finally {
  await browser.close()
  await server.close()
}
