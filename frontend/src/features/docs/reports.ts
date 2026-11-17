import { report } from './sources'

/** What every generated report says about itself (R-07). */
export interface Provenance {
  gitSha: string
  generatedAt: string
  corpusRelease: string
}

function record(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null
}

function text(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}

function number(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function provenance(r: Record<string, unknown>): Provenance | null {
  const gitSha = text(r['gitSha'])
  const generatedAt = text(r['generatedAt'])
  const corpusRelease = text(r['corpusRelease'])
  return gitSha === null || generatedAt === null || corpusRelease === null
    ? null
    : { gitSha, generatedAt, corpusRelease }
}

export interface RetrievalRow {
  method: string
  questions: number
  hitsAt5: number
  hitAt5: number
  mrrAt10: number
  recallAt8: number
  meanMillis: number
}

export interface SkippedRow {
  method: string
  reason: string
}

export interface RetrievalReport {
  provenance: Provenance
  embeddingModel: string
  embeddedSections: number
  questions: number
  rows: RetrievalRow[]
  skipped: SkippedRow[]
}

/** The retrieval report, or `null` when `eval/reports/retrieval.json` is not in the build. */
export function retrievalReport(): RetrievalReport | null {
  const r = record(report('retrieval'))
  const rows = r?.['rows']
  const p = r === null ? null : provenance(r)
  if (r === null || p === null || !Array.isArray(rows)) {
    return null
  }
  const done: RetrievalRow[] = []
  const skipped: SkippedRow[] = []
  for (const raw of rows as unknown[]) {
    const row = record(raw)
    const method = text(row?.['method'])
    if (row === null || method === null) {
      continue
    }
    const reason = text(row['skipped'])
    const questions = number(row['questions'])
    const hitsAt5 = number(row['hitsAt5'])
    const hitAt5 = number(row['hitAt5'])
    const mrrAt10 = number(row['mrrAt10'])
    const recallAt8 = number(row['recallAt8'])
    const meanMillis = number(row['meanMillis'])
    if (reason !== null) {
      skipped.push({ method, reason })
    } else if (
      questions !== null &&
      hitsAt5 !== null &&
      hitAt5 !== null &&
      mrrAt10 !== null &&
      recallAt8 !== null &&
      meanMillis !== null
    ) {
      done.push({ method, questions, hitsAt5, hitAt5, mrrAt10, recallAt8, meanMillis })
    }
  }
  return {
    provenance: p,
    embeddingModel: text(r['embeddingModel']) ?? '',
    embeddedSections: number(r['embeddedSections']) ?? 0,
    questions: number(r['questions']) ?? 0,
    rows: done,
    skipped,
  }
}

export interface ActQuality {
  code: string
  title: string
  authoritativeLang: string
  sourceUrl: string
  versions: number
  currentSections: number
  omittedSections: number
  missingTranslation: number
}

export interface CorpusQuality {
  provenance: Provenance
  status: string
  acts: ActQuality[]
  edges: Record<string, number>
  unresolvedReferences: number
  ilo: { code: string; label: string; status: string }[]
  missing: { what: string; reason: string }[]
}

/** The corpus quality report, or `null` when `eval/reports/corpus-quality.json` is not in the build. */
export function corpusQuality(): CorpusQuality | null {
  const r = record(report('corpus-quality'))
  const p = r === null ? null : provenance(r)
  if (r === null || p === null || !Array.isArray(r['acts'])) {
    return null
  }
  const acts: ActQuality[] = []
  for (const raw of r['acts'] as unknown[]) {
    const a = record(raw)
    const code = text(a?.['code'])
    if (a === null || code === null) {
      continue
    }
    acts.push({
      code,
      title: text(a['title']) ?? code,
      authoritativeLang: text(a['authoritativeLang']) ?? '',
      sourceUrl: text(a['sourceUrl']) ?? '',
      versions: number(a['versions']) ?? 0,
      currentSections: number(a['currentSections']) ?? 0,
      omittedSections: number(a['omittedSections']) ?? 0,
      missingTranslation: number(a['missingTranslation']) ?? 0,
    })
  }
  const edges: Record<string, number> = {}
  for (const [key, value] of Object.entries(record(r['edges']) ?? {})) {
    const n = number(value)
    if (n !== null) {
      edges[key] = n
    }
  }
  const ilo = (Array.isArray(r['ilo']) ? (r['ilo'] as unknown[]) : []).flatMap((raw) => {
    const row = record(raw)
    const code = text(row?.['code'])
    return row === null || code === null
      ? []
      : [{ code, label: text(row['titleLabel']) ?? '', status: text(row['status']) ?? '' }]
  })
  const missing = (Array.isArray(r['missing']) ? (r['missing'] as unknown[]) : []).flatMap(
    (raw) => {
      const row = record(raw)
      const what = text(row?.['what'])
      return row === null || what === null ? [] : [{ what, reason: text(row['reason']) ?? '' }]
    },
  )
  return {
    provenance: p,
    status: text(r['status']) ?? '',
    acts,
    edges,
    unresolvedReferences: number(r['unresolvedReferences']) ?? 0,
    ilo,
    missing,
  }
}

/** One headline row of the contract evaluation, as the report states it (with counts). */
export interface HeadlineRow {
  id: string
  metric: string
  baseline: string
  shromikai: string
  target: string
  met: boolean | null
}

/** A metric that was not measured, with the report's own reason. */
export interface UnmeasuredRow {
  id: string
  name: string
  status: string
  note: string
}

export interface ContractReport {
  provenance: Provenance
  contracts: number
  labelFiles: number
  reviewedLabelFiles: number
  adversarialCases: number
  liveSpendMicros: number
  verifierIndependence: string
  split: string
  headline: HeadlineRow[]
  unmeasured: UnmeasuredRow[]
  limitations: string[]
  knownFailures: string[]
}

function strings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
}

/**
 * The contract evaluation (`eval/reports/report.json`, generated by `./gradlew evaluate`), or
 * `null` when it has not been generated. Values are shown exactly as the report states them.
 */
export function contractReport(raw: unknown = report('report')): ContractReport | null {
  const r = record(raw)
  const p = record(r?.['provenance'])
  const provenanceValue = p === null ? null : provenance(p)
  const config = record(r?.['config'])
  if (r === null || provenanceValue === null || config === null) {
    return null
  }
  const headline = (Array.isArray(r['headline']) ? (r['headline'] as unknown[]) : []).flatMap(
    (raw) => {
      const row = record(raw)
      const id = text(row?.['id'])
      if (row === null || id === null) {
        return []
      }
      const met = row['met']
      return [
        {
          id,
          metric: text(row['metric']) ?? '',
          baseline: text(row['baseline']) ?? '',
          shromikai: text(row['shromikai']) ?? '',
          target: text(row['target']) ?? '',
          met: typeof met === 'boolean' ? met : null,
        },
      ]
    },
  )
  const ours = record(record(r['metrics'])?.['shromikai'])
  const unmeasured = Object.values(ours ?? {}).flatMap((raw) => {
    const m = record(raw)
    const id = text(m?.['id'])
    const status = text(m?.['status'])
    return m === null || id === null || status === null || status === 'MEASURED'
      ? []
      : [{ id, name: text(m['name']) ?? '', status, note: text(m['note']) ?? '' }]
  })
  return {
    provenance: provenanceValue,
    contracts: strings(config['contracts']).length,
    labelFiles: number(config['labelFiles']) ?? 0,
    reviewedLabelFiles: number(config['reviewedLabelFiles']) ?? 0,
    adversarialCases: number(config['adversarialCases']) ?? 0,
    liveSpendMicros: number(config['liveSpendMicros']) ?? 0,
    verifierIndependence: text(config['verifierIndependence']) ?? '',
    split: text(config['split']) ?? '',
    headline,
    unmeasured,
    limitations: strings(r['limitations']),
    knownFailures: strings(r['knownFailures']),
  }
}
