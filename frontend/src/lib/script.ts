/** Which language a text is written in, judged by its letters: Bangla script or Latin script. */
export function scriptOf(text: string): 'bn' | 'en' | null {
  let bangla = 0
  let latin = 0
  for (const char of text) {
    if (/[ঀ-৿]/u.test(char)) {
      bangla += 1
    } else if (/[A-Za-z]/.test(char)) {
      latin += 1
    }
  }
  if (bangla + latin === 0) {
    return null
  }
  return bangla >= latin ? 'bn' : 'en'
}

/** True when two texts are written in different languages, so a word-level diff means nothing. */
export function differentLanguages(a: string | null, b: string | null): boolean {
  if (a === null || b === null) {
    return false
  }
  const first = scriptOf(a)
  const second = scriptOf(b)
  return first !== null && second !== null && first !== second
}
