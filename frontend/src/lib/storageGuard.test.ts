/// <reference types="node" />
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function sources(directory: string): string[] {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name)
    if (statSync(path).isDirectory()) {
      return sources(path)
    }
    return /\.(ts|tsx)$/.test(name) && !/\.test\.(ts|tsx)$/.test(name) ? [path] : []
  })
}

describe('StorageGuard (static)', () => {
  const root = resolve(process.cwd(), 'src')

  it('R-02 no source file but lib/storage.ts touches a browser store or a service worker', () => {
    const offenders = sources(root)
      .filter(
        (path) => !path.endsWith(join('lib', 'storage.ts')) && !path.includes(join('src', 'test')),
      )
      .filter((path) =>
        /\b(localStorage|sessionStorage|indexedDB|caches\b|serviceWorker|persistQueryClient)/.test(
          readFileSync(path, 'utf8'),
        ),
      )
      .map((path) => relative(root, path))

    expect(offenders).toEqual([])
  })

  it('R-02 the query cache has no persister and keeps results at most 30 minutes', () => {
    const main = readFileSync(join(root, 'main.tsx'), 'utf8')

    expect(main).not.toMatch(/persistQueryClient|StoragePersister|PersistQueryClientProvider/)
    expect(main).toContain('gcTime: 30 * 60 * 1000')
  })
})
