// The lite landing must stay under 1.5 MB of total transfer (ADR-0023): a phone-width visit (the
// lite tier by default) to the production build, counted from the network bytes Chromium actually
// received (CDP encodedDataLength), fonts, poster and API calls included. Run after `npm run build`.
import { chromium } from '@playwright/test'
import { preview } from 'vite'

const LIMIT = 1.5 * 1024 * 1024
const server = await preview({ preview: { port: 0, strictPort: false }, logLevel: 'error' })
const address = server.httpServer.address()
const port = typeof address === 'object' && address !== null ? address.port : 0
const browser = await chromium.launch()
let failed = false
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
  const cdp = await page.context().newCDPSession(page)
  await cdp.send('Network.enable')
  const sizes = new Map()
  cdp.on('Network.responseReceived', (event) => {
    sizes.set(event.requestId, { url: event.response.url, bytes: 0 })
  })
  cdp.on('Network.loadingFinished', (event) => {
    const entry = sizes.get(event.requestId)
    if (entry !== undefined) entry.bytes = event.encodedDataLength
  })
  await page.goto(`http://localhost:${port}/`)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(1500)
  const tier = await page.locator('[data-tier]').getAttribute('data-tier')
  const list = [...sizes.values()].sort((a, b) => b.bytes - a.bytes)
  const total = list.reduce((sum, entry) => sum + entry.bytes, 0)
  for (const entry of list.slice(0, 8)) {
    console.log(
      `  ${(entry.bytes / 1024).toFixed(1).padStart(7)} KB  ${entry.url.replace(/^https?:\/\/[^/]+/, '')}`,
    )
  }
  console.log(
    `lite landing (tier ${tier}): ${(total / 1024).toFixed(1)} KB over ${list.length} requests (limit 1536 KB)`,
  )
  if (tier !== 'lite') {
    console.error('check-lite-transfer: a 390 px visit did not get the lite tier')
    failed = true
  }
  if (list.some((entry) => /CinematicScene|three\.module|ContractScene/.test(entry.url))) {
    console.error('check-lite-transfer: the lite landing fetched scene code')
    failed = true
  }
  if (total > LIMIT) {
    console.error('check-lite-transfer: the lite landing exceeds 1.5 MB')
    failed = true
  }
} finally {
  await browser.close()
  server.httpServer.close()
}
process.exit(failed ? 1 : 0)
