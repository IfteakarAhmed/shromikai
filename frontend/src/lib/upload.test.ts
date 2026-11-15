import { describe, expect, it } from 'vitest'
import {
  classifyFile,
  MAX_BYTES,
  MAX_IMAGES,
  MAX_TEXT,
  MIN_TEXT,
  validateImages,
  validatePdf,
  validateText,
} from './upload'
import { fitWithin, MAX_EDGE, MAX_IMAGE_BYTES, nextQuality } from './image'

function file(name: string, type: string, size = 1000): File {
  const f = new File(['x'], name, { type })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

describe('classifyFile', () => {
  it('FR-69 sorts a file into pdf, image or text by type, and by extension when the type is empty', () => {
    expect(classifyFile(file('a.pdf', 'application/pdf'))).toBe('pdf')
    expect(classifyFile(file('a.PDF', ''))).toBe('pdf')
    expect(classifyFile(file('p.jpg', 'image/jpeg'))).toBe('image')
    expect(classifyFile(file('p.png', 'image/png'))).toBe('image')
    expect(classifyFile(file('n.txt', 'text/plain'))).toBe('text')
    expect(classifyFile(file('n.md', ''))).toBe('text')
  })

  it('FR-69 refuses types the API cannot read', () => {
    expect(classifyFile(file('a.docx', 'application/vnd.openxmlformats'))).toBe('unsupported')
    expect(classifyFile(file('p.gif', 'image/gif'))).toBe('unsupported')
    expect(classifyFile(file('p.webp', 'image/webp'))).toBe('unsupported')
    expect(classifyFile(file('run.exe', 'application/x-msdownload'))).toBe('unsupported')
  })
})

describe('validatePdf', () => {
  it('FR-69 accepts a PDF up to 10 MB and refuses a larger one', () => {
    expect(validatePdf(file('a.pdf', 'application/pdf', MAX_BYTES))).toBeNull()
    expect(validatePdf(file('a.pdf', 'application/pdf', MAX_BYTES + 1))).toBe('tooLarge')
  })
})

describe('validateImages', () => {
  it('FR-69 accepts up to 15 images and refuses none, a 16th, or other types', () => {
    const images = Array.from({ length: MAX_IMAGES }, (_, i) => file(`${i}.jpg`, 'image/jpeg'))

    expect(validateImages(images)).toBeNull()
    expect(validateImages([])).toBe('noInput')
    expect(validateImages([...images, file('x.jpg', 'image/jpeg')])).toBe('tooManyImages')
    expect(validateImages([file('a.gif', 'image/gif')])).toBe('unsupportedType')
  })

  it('FR-69 refuses images that are together over 10 MB', () => {
    const big = [file('a.jpg', 'image/jpeg', MAX_BYTES), file('b.jpg', 'image/jpeg', 1)]

    expect(validateImages(big)).toBe('tooLarge')
  })
})

describe('validateText', () => {
  it('FR-69 needs 200 to 60,000 characters of real text', () => {
    expect(validateText('')).toBe('noInput')
    expect(validateText('a'.repeat(MIN_TEXT - 1))).toBe('textTooShort')
    expect(validateText('a'.repeat(MIN_TEXT))).toBeNull()
    expect(validateText('a'.repeat(MAX_TEXT))).toBeNull()
    expect(validateText('a'.repeat(MAX_TEXT + 1))).toBe('textTooLong')
  })

  it('FR-69 does not count surrounding blank space', () => {
    expect(validateText(`   ${'a'.repeat(MIN_TEXT - 1)}   `)).toBe('textTooShort')
  })
})

describe('fitWithin', () => {
  it('FR-69 scales a 4000 x 3000 photo to at most 1600 px on the long edge, keeping the ratio', () => {
    expect(fitWithin(4000, 3000, MAX_EDGE)).toEqual({ width: 1600, height: 1200 })
    expect(fitWithin(3000, 4000, MAX_EDGE)).toEqual({ width: 1200, height: 1600 })
  })

  it('FR-69 never enlarges a small image', () => {
    expect(fitWithin(800, 600, MAX_EDGE)).toEqual({ width: 800, height: 600 })
  })
})

describe('nextQuality', () => {
  it('FR-69 lowers the JPEG quality until the picture is within 1 MB, and stops at a floor', () => {
    expect(nextQuality(0.9, MAX_IMAGE_BYTES + 1)).toBeLessThan(0.9)
    expect(nextQuality(0.9, MAX_IMAGE_BYTES)).toBeNull()
    expect(nextQuality(0.3, MAX_IMAGE_BYTES + 1)).toBeNull()
  })
})
