/// <reference types="vitest/config" />
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * Fills %SITE_URL% in index.html with the public origin of the deployment (env SITE_URL, no
 * trailing slash). Without it the value is empty and the social-image URL stays relative.
 */
function siteUrl(): Plugin {
  return {
    name: 'shromikai-site-url',
    transformIndexHtml: {
      order: 'pre',
      handler: (html) =>
        html.replaceAll('%SITE_URL%', (process.env.SITE_URL ?? '').replace(/\/$/, '')),
    },
  }
}

export default defineConfig({
  plugins: [siteUrl(), react(), tailwindcss()],
  // The manifest lets check:bundle follow each lazy chunk to the shared chunks it imports.
  build: { manifest: true },
  server: {
    // The docs hub reads ../docs and ../eval/reports at build time (FR-74).
    fs: { allow: ['..'] },
    // Forward API calls to the Spring Boot backend during development.
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.test.{ts,tsx}',
        'src/test/**',
        'src/api/types.ts',
        'src/main.tsx',
        'src/fonts.ts',
        'src/vite-env.d.ts',
      ],
      thresholds: { statements: 70 },
      reporter: ['text-summary', 'html', 'lcov'],
    },
  },
})
