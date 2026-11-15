/**
 * What the upload form accepts (api.md §4.1, FR-69): one PDF, one to fifteen JPEG or PNG photos,
 * or 200 to 60,000 characters of text, within 10 MB. The API checks the real file type again by its
 * magic bytes; these checks give the reader an answer before anything is sent.
 */

export const MAX_BYTES = 10 * 1024 * 1024
export const MAX_IMAGES = 15
export const MIN_TEXT = 200
export const MAX_TEXT = 60_000

export type UploadError =
  'noInput' | 'tooLarge' | 'unsupportedType' | 'tooManyImages' | 'textTooShort' | 'textTooLong'

export type FileKind = 'pdf' | 'image' | 'text' | 'unsupported'

function extension(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

/** The kind of a file by its type, or by its extension when the browser gives no type. */
export function classifyFile(file: Pick<File, 'name' | 'type'>): FileKind {
  const type = file.type.toLowerCase()
  const ext = extension(file.name)
  if (type === 'application/pdf' || (type === '' && ext === 'pdf')) {
    return 'pdf'
  }
  if (type === 'image/jpeg' || type === 'image/png') {
    return 'image'
  }
  if (type === '' && (ext === 'jpg' || ext === 'jpeg' || ext === 'png')) {
    return 'image'
  }
  if (
    type === 'text/plain' ||
    type === 'text/markdown' ||
    (type === '' && ['txt', 'md'].includes(ext))
  ) {
    return 'text'
  }
  return 'unsupported'
}

export function validatePdf(file: Pick<File, 'name' | 'type' | 'size'>): UploadError | null {
  if (classifyFile(file) !== 'pdf') {
    return 'unsupportedType'
  }
  return file.size > MAX_BYTES ? 'tooLarge' : null
}

export function validateImages(
  files: readonly Pick<File, 'name' | 'type' | 'size'>[],
): UploadError | null {
  if (files.length === 0) {
    return 'noInput'
  }
  if (files.length > MAX_IMAGES) {
    return 'tooManyImages'
  }
  if (files.some((file) => classifyFile(file) !== 'image')) {
    return 'unsupportedType'
  }
  return files.reduce((sum, file) => sum + file.size, 0) > MAX_BYTES ? 'tooLarge' : null
}

export function validateText(text: string): UploadError | null {
  const length = text.trim().length
  if (length === 0) {
    return 'noInput'
  }
  if (length < MIN_TEXT) {
    return 'textTooShort'
  }
  return length > MAX_TEXT ? 'textTooLong' : null
}
