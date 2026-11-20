// Renders the brand PNGs from their SVG sources with headless Chromium (ADR-0028):
//
//   node scripts/render-brand.mjs
//
// scripts/brand/*.source.svg come from scripts/brand/build-brand.py; their type is already outlined,
// so the render needs no fonts and no network.
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from '@playwright/test'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const targets = [
  { source: 'favicon-32', out: 'public/favicon-32.png', width: 32, height: 32 },
  { source: 'apple-touch-icon', out: 'public/apple-touch-icon.png', width: 180, height: 180 },
  { source: 'og-image', out: 'public/og-image.png', width: 1200, height: 630 },
]

const browser = await chromium.launch()
try {
  for (const { source, out, width, height } of targets) {
    const svg = readFileSync(resolve(root, `scripts/brand/${source}.source.svg`), 'utf8')
    const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 })
    await page.route('**/*', (route) => route.abort())
    await page.setContent(
      `<!doctype html><style>html,body{margin:0}svg{display:block;width:${width}px;height:${height}px}</style>${svg}`,
    )
    const bytes = await page.screenshot({ clip: { x: 0, y: 0, width, height } })
    await page.close()
    writeFileSync(resolve(root, out), bytes)
    console.log(`${out}: ${(bytes.length / 1024).toFixed(1)} KB`)
  }
} finally {
  await browser.close()
}
