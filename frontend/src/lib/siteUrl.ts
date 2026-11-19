/**
 * The public origin of a deployment for the absolute social-preview URLs of index.html (FR-85,
 * ADR-0026, ADR-0027). First set wins: VITE_SITE_URL, SITE_URL (the Space image), then the
 * production host that Vercel provides at build time. Empty when none is set, so development and CI
 * builds keep relative URLs. Pure, so vite.config.ts and the tests share it.
 */
export function resolveSiteUrl(env: Record<string, string | undefined>): string {
  const raw = [env['VITE_SITE_URL'], env['SITE_URL'], env['VERCEL_PROJECT_PRODUCTION_URL']]
    .map((value) => value?.trim() ?? '')
    .find((value) => value !== '')
  if (raw === undefined) {
    return ''
  }
  const withScheme = /^https?:\/\//i.test(raw) ? raw : `https://${raw}`
  return withScheme.replace(/\/+$/, '')
}
