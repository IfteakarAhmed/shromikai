export type ReplayLanguage = 'bn' | 'en'
export const LOGIN_FILE: string
export const ACCEPTED_FILE: string
export const MANIFEST_FILE: string
export function metaFile(language: ReplayLanguage): string
export function analysisFile(language: ReplayLanguage): string
export function traceFile(language: ReplayLanguage): string
export function memoFile(language: ReplayLanguage): string
export function sectionFile(
  actCode: string,
  sectionNo: string,
  versionLabel: string,
  language: ReplayLanguage,
): string
