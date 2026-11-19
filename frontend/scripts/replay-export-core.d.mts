export interface SectionRef {
  actCode: string
  sectionNo: string
  versionLabel: string
}
export interface Redaction {
  path: string
  reason: string
}
export interface Redacted<T> {
  value: T
  redactions: Redaction[]
}
export function sha256(bytes: Uint8Array): string
export function collectSectionRefs(value: unknown): SectionRef[]
export function redactMeta<T>(meta: T): Redacted<T>
export function redactLogin<T>(login: T): Redacted<T>
export function redactResult<T>(result: T): Redacted<T>
export function assertNoSecrets(text: string, name: string): void
