/// <reference types="vitest/config" />
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { rmSync } from 'node:fs'
import tailwindcss from '@tailwindcss/vite'
import { resolveSiteUrl } from './src/lib/siteUrl'

/**
 * Fills %SITE_URL% in index.html with the public origin of the deployment (VITE_SITE_URL,
 * SITE_URL or Vercel's production host; see resolveSiteUrl). Without any the value is empty and the
 * social-image URL stays relative.
 */
function siteUrl(): Plugin {
  return {
    name: 'shromikai-site-url',
    transformIndexHtml: {
      order: 'pre',
      handler: (html) => html.replaceAll('%SITE_URL%', resolveSiteUrl(process.env)),
    },
  }
}

/**
 * The exported replay files in public/replay belong to the static recorded replay only
 * (VITE_STATIC_REPLAY=1, ADR-0027): every other build leaves them out of dist.
 */
function replayFilesOnlyForReplay(): Plugin {
  let outDir = 'dist'
  return {
    name: 'shromikai-replay-files',
    apply: 'build',
    configResolved: (config) => {
      outDir = config.build.outDir
    },
    closeBundle: () => {
      if (process.env.VITE_STATIC_REPLAY !== '1') {
        rmSync(`${outDir}/replay`, { recursive: true, force: true })
      }
    },
  }
}

export default defineConfig({
  plugins: [siteUrl(), replayFilesOnlyForReplay(), react(), tailwindcss()],
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
