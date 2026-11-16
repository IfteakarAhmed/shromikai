import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end and accessibility checks (test-strategy §12). They run against the real backend in the
 * `demo` and `fake-models` profiles (scripts/demo-backend.sh): recorded model answers for the
 * synthetic sample contract, the real corpus and the real verifier. No cloud call is possible.
 * Start the database and Ollama first (scripts/dev-up.sh).
 */
/**
 * Two demo modes of the backend (scripts/demo-backend.sh). The default runs with the legal rules on
 * (13 findings, none uncertain). `DEMO_RULES=off npm run e2e` runs the backend without a rule file,
 * so the earlier recordings replay (uncertain findings, the "no active rule" notice) and runs only
 * the tests tagged @rules-off; the default run leaves those out.
 */
const rulesOff = process.env['DEMO_RULES'] === 'off'

export default defineConfig({
  grep: rulesOff ? /@rules-off/ : undefined,
  grepInvert: rulesOff ? undefined : /@rules-off/,
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 1,
  timeout: 90_000,
  expect: { timeout: 20_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'mobile-chromium',
      use: {
        ...devices['Pixel 7'],
        viewport: { width: 390, height: 844 },
      },
    },
    {
      name: 'desktop-chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } },
    },
  ],
  webServer: [
    {
      command: '../scripts/demo-backend.sh',
      url: 'http://localhost:8080/api/v1/meta',
      timeout: 240_000,
      reuseExistingServer: true,
      env: { E2E: '1', DEMO_RULES: rulesOff ? 'off' : 'on' },
    },
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      timeout: 60_000,
      reuseExistingServer: true,
    },
  ],
})
