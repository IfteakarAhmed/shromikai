// Fails when the initial JavaScript of the production build exceeds 200 KB gzip (NFR-10).
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
