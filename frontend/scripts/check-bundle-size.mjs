// Bundle budgets (NFR-10, ADR-0023):
// - the initial JavaScript of the production build: at most 200 KB gzip;
// - the standard 3D hero (its lazy chunk with the shared three.js chunk, plus the poster): 200 KB;
// - the cinematic landing scene (its lazy chunk with everything it imports, three.js included):
//   300 KB gzip;
// - the self-hosted fonts: 320 KB of woff2 at most, only the subsets and weights of the pairing.
// Lazy chunks are followed through the Vite manifest, so a shared chunk counts for each scene.
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

const KB = 1024
const dist = new URL('../dist/', import.meta.url).pathname
const html = readFileSync(join(dist, 'index.html'), 'utf8')
const manifestPath = join(dist, '.vite', 'manifest.json')
if (!existsSync(manifestPath)) {
  console.error(
    'check-bundle-size: dist/.vite/manifest.json is missing (build.manifest in vite.config)',
  )
  process.exit(1)
}
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
const gzipped = (file) => gzipSync(readFileSync(join(dist, file))).length
const kb = (bytes) => `${(bytes / KB).toFixed(1)} KB`
let failed = false

// 1. Initial JavaScript: the scripts and module preloads in index.html.
const initial = [
  ...html.matchAll(/<(?:script|link)[^>]+(?:src|href)="\/(assets\/[^"]+\.js)"/g),
].map((match) => match[1])
if (initial.length === 0) {
  console.error('check-bundle-size: no initial script found in dist/index.html')
  process.exit(1)
}
const initialBytes = initial.reduce((sum, file) => sum + gzipped(file), 0)
console.log(`initial JavaScript: ${kb(initialBytes)} gzip (limit 200 KB)`)
if (initialBytes > 200 * KB) {
  console.error('check-bundle-size: initial JavaScript exceeds 200 KB gzip (NFR-10)')
  failed = true
}

/** A lazy module's own chunk plus every chunk it imports statically, minus the initial ones. */
function lazyBytes(source) {
  const entry = manifest[source]
  if (entry === undefined) {
    console.error(`check-bundle-size: ${source} is not in the build manifest`)
    process.exit(1)
  }
  const files = new Set()
  const visit = (key) => {
    const item = manifest[key]
    if (item === undefined || files.has(item.file)) {
      return
    }
    if (!initial.includes(item.file)) {
      files.add(item.file)
    }
    for (const next of item.imports ?? []) {
      visit(next)
    }
  }
  visit(source)
  const parts = [...files].map((file) => ({ file, bytes: gzipped(file) }))
  return { parts, bytes: parts.reduce((sum, part) => sum + part.bytes, 0) }
}

// 2. The standard hero: scene chunk(s) and the poster it fades in over (a webp does not gzip).
const hero = lazyBytes('src/features/landing/ContractScene.tsx')
const poster = Object.values(manifest)
  .flatMap((item) => item.assets ?? [])
  .find((file) => /contract-poster-.*\.webp$/.test(file))
if (poster === undefined) {
  console.error('check-bundle-size: the poster is missing from the build')
  process.exit(1)
}
const posterBytes = readFileSync(join(dist, poster)).length
console.log(
  `standard hero: ${kb(hero.bytes)} scene (${hero.parts.map((p) => `${p.file} ${kb(p.bytes)}`).join(', ')}) + ${kb(posterBytes)} poster = ${kb(hero.bytes + posterBytes)} (limit 200 KB)`,
)
if (hero.bytes + posterBytes > 200 * KB) {
  console.error('check-bundle-size: the standard 3D hero exceeds 200 KB (ADR-0023)')
  failed = true
}

// 3. The cinematic landing scene, three.js included.
const cinematic = lazyBytes('src/features/landing/cinematic/CinematicScene.tsx')
console.log(
  `cinematic scene: ${kb(cinematic.bytes)} gzip (${cinematic.parts.map((p) => `${p.file} ${kb(p.bytes)}`).join(', ')}) (limit 300 KB)`,
)
if (cinematic.bytes > 300 * KB) {
  console.error('check-bundle-size: the cinematic scene exceeds 300 KB gzip (ADR-0023)')
  failed = true
}
if (initial.some((file) => /CinematicScene|three\.module/.test(file))) {
  console.error('check-bundle-size: cinematic code leaked into the initial JavaScript')
  failed = true
}
// 4. Fonts (C1, ADR-0023): every self-hosted woff2 in the build. A page fetches only the subsets
// it uses, so this is the upper bound of what a visit can download in fonts.
const fontFiles = readdirSync(join(dist, 'assets')).filter((name) => name.endsWith('.woff2'))
const fontBytes = fontFiles.reduce(
  (sum, name) => sum + readFileSync(join(dist, 'assets', name)).length,
  0,
)
console.log(`fonts: ${kb(fontBytes)} in ${String(fontFiles.length)} woff2 files (limit 320 KB)`)
if (fontBytes > 320 * KB) {
  console.error('check-bundle-size: self-hosted fonts exceed 320 KB (ADR-0023)')
  failed = true
}
if (
  fontFiles.some(
    (name) =>
      /latin-ext|cyrillic|vietnamese|greek|italic|-700-/.test(name) &&
      !/space-grotesk-latin-700/.test(name),
  )
) {
  console.error('check-bundle-size: a font subset or weight outside the pairing was bundled')
  failed = true
}
process.exit(failed ? 1 : 0)
