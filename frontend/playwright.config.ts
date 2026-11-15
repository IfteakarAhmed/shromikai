import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end and accessibility checks (test-strategy §12). They run against the real backend in the
 * `demo` and `fake-models` profiles (scripts/demo-backend.sh): recorded model answers for the
 * synthetic sample contract, the real corpus and the real verifier. No cloud call is possible.
 * Start the database and Ollama first (scripts/dev-up.sh).
 */
export default defineConfig({
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
      env: { E2E: '1' },
    },
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      timeout: 60_000,
      reuseExistingServer: true,
    },
  ],
})
