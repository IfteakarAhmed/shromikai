// Fails when the initial JavaScript of the production build exceeds 200 KB gzip (NFR-10), or when
// the 3D hero (its lazy scene chunk plus the poster picture) exceeds 200 KB (ADR-0023).
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

const LIMIT_BYTES = 200 * 1024
const dist = new URL('../dist/', import.meta.url).pathname
const html = readFileSync(join(dist, 'index.html'), 'utf8')
const initial = [
  ...html.matchAll(/<(?:script|link)[^>]+(?:src|href)="\/(assets\/[^"]+\.js)"/g),
].map((match) => match[1])
if (initial.length === 0) {
  console.error('check-bundle-size: no initial script found in dist/index.html')
  process.exit(1)
}
let total = 0
for (const file of initial) {
  const size = gzipSync(readFileSync(join(dist, file))).length
  total += size
  console.log(`${file}: ${(size / 1024).toFixed(1)} KB gzip`)
}
const all = readdirSync(join(dist, 'assets')).filter((name) => name.endsWith('.js')).length
console.log(
  `initial JavaScript: ${(total / 1024).toFixed(1)} KB gzip (limit 200 KB; ${all} chunks)`,
)
if (total > LIMIT_BYTES) {
  console.error('check-bundle-size: initial JavaScript exceeds 200 KB gzip (NFR-10)')
  process.exit(1)
}

// The hero: the lazy scene chunk (gzip) and the poster it fades in over (a webp does not gzip).
const assets = readdirSync(join(dist, 'assets'))
const scene = assets.find((name) => /^ContractScene-.*\.js$/.test(name))
const poster = assets.find((name) => /^contract-poster-.*\.webp$/.test(name))
if (scene === undefined || poster === undefined) {
  console.error('check-bundle-size: the hero scene chunk or its poster is missing from the build')
  process.exit(1)
}
const sceneBytes = gzipSync(readFileSync(join(dist, 'assets', scene))).length
const posterBytes = readFileSync(join(dist, 'assets', poster)).length
console.log(
  `hero: ${(sceneBytes / 1024).toFixed(1)} KB scene + ${(posterBytes / 1024).toFixed(1)} KB poster = ${((sceneBytes + posterBytes) / 1024).toFixed(1)} KB (limit 200 KB)`,
)
if (sceneBytes + posterBytes > LIMIT_BYTES) {
  console.error('check-bundle-size: the 3D hero exceeds 200 KB (ADR-0023)')
  process.exit(1)
}
