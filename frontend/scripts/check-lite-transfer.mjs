// The lite landing must stay under 1.5 MB of total transfer (ADR-0023): a phone-width visit (the
// lite tier by default) to the production build, counted from the network bytes Chromium actually
// received (CDP encodedDataLength), fonts, poster and API calls included. Run after `npm run build`.
//
// NFR-10 note (honest scope): the second part of this script loads the same page once under CDP
// network throttling (400 kbps, 400 ms RTT, the "Slow 3G" numbers of the PRD) and PRINTS the time
// until the load event and until the first link or button is visible. That is a proxy for
// "interactive" (it does not measure main-thread blocking, and one run is one sample on a local
// preview server without compression unless the build served it compressed), so it is reported and
// bounded by a generous guard (SLOW_GUARD_MS), not used to claim "interactive in 5 s". The measured
// time is recorded in docs/spec/PRD.md (NFR-10 note) and docs/known-gaps.md. Measured on
// 2026-11-17, 3 runs: load event 4.7 s, first link or button visible 4.8 to 4.9 s (target 5 s).
import { chromium } from '@playwright/test'
import { preview } from 'vite'

const LIMIT = 1.5 * 1024 * 1024
const SLOW_GUARD_MS = 30_000
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
  // Throttled run: a fresh context so nothing is cached.
  const slowContext = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const slowPage = await slowContext.newPage()
  const slowCdp = await slowContext.newCDPSession(slowPage)
  await slowCdp.send('Network.enable')
  await slowCdp.send('Network.emulateNetworkConditions', {
    offline: false,
    latency: 400,
    downloadThroughput: (400 * 1024) / 8,
    uploadThroughput: (400 * 1024) / 8,
  })
  const t0 = Date.now()
  await slowPage.goto(`http://localhost:${port}/`, { waitUntil: 'load', timeout: 120_000 })
  const loadMs = Date.now() - t0
  await slowPage
    .locator('main a, main button')
    .first()
    .waitFor({ state: 'visible', timeout: 120_000 })
  const visibleMs = Date.now() - t0
  console.log(
    `slow-3G emulation (400 kbps, 400 ms RTT): load event ${(loadMs / 1000).toFixed(1)} s, first link or button visible ${(visibleMs / 1000).toFixed(1)} s`,
  )
  if (visibleMs > SLOW_GUARD_MS) {
    console.error(`check-lite-transfer: throttled landing took more than ${SLOW_GUARD_MS / 1000} s`)
    failed = true
  }
  await slowContext.close()
} finally {
  await browser.close()
  server.httpServer.close()
}
process.exit(failed ? 1 : 0)
