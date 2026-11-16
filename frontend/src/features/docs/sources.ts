import { useQuery } from '@tanstack/react-query'

/**
 * The project's own documents and eval reports, read when the app is built (FR-74). Nothing here is
 * typed by hand: a number shown on a docs page comes from a generated file and carries that file's
 * own commit, date and corpus release (R-07).
 */
type Loader = () => Promise<string>

const markdownFiles = import.meta.glob(
  [
    '../../../../docs/*.md',
    '../../../../docs/prompts/*.md',
    '../../../../docs/spec/architecture.md',
  ],
  { query: '?raw', import: 'default' },
) as Record<string, Loader>

const reportFiles = import.meta.glob<unknown>('../../../../eval/reports/*.json', {
  import: 'default',
  eager: true,
})

function pathOf(file: string): string {
  return file.replace(/^(\.\.\/)+/, '')
}

const LOADERS = new Map<string, Loader>(
  Object.entries(markdownFiles).map(([file, loader]) => [pathOf(file), loader]),
)

/** The repository paths of the prompt explanations, for example `docs/prompts/risk-analysis.md`. */
export const PROMPT_IDS: string[] = [...LOADERS.keys()]
  .filter((path) => /^docs\/prompts\/[a-z-]+\.md$/.test(path) && !path.endsWith('/README.md'))
  .map((path) => path.slice('docs/prompts/'.length, -'.md'.length))
  .sort()

/** One generated eval report by name (without `.json`), or `undefined` when it has not been generated. */
export function report(name: string): unknown {
  for (const [file, value] of Object.entries(reportFiles)) {
    if (pathOf(file) === `eval/reports/${name}.json`) {
      return value
    }
  }
  return undefined
}

/** The text of a repository document, loaded on demand; `null` when it is not in the build. */
export function useDocument(path: string) {
  return useQuery({
    queryKey: ['doc', path],
    queryFn: async () => {
      const loader = LOADERS.get(path)
      return loader === undefined ? null : await loader()
    },
    staleTime: Infinity,
    gcTime: 5 * 60 * 1000,
  })
}
