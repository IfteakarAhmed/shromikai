/** An address safe to put in a link: only http and https (an official source never needs more). */
export function safeHref(value: string | null | undefined): string | null {
  if (value === null || value === undefined) {
    return null
  }
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.toString() : null
  } catch {
    return null
  }
}
