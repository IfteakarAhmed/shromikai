import { CanvasTexture, LinearMipmapLinearFilter, NoColorSpace, SRGBColorSpace } from 'three'

/** A small seeded random generator (mulberry32), so the generated pictures are the same each run. */
export function seeded(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function roundRect(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
) {
  const r = Math.min(height / 2, width / 2)
  context.beginPath()
  context.moveTo(x + r, y)
  context.arcTo(x + width, y, x + width, y + height, r)
  context.arcTo(x + width, y + height, x, y + height, r)
  context.arcTo(x, y + height, x, y, r)
  context.arcTo(x, y, x + width, y, r)
  context.closePath()
  context.fill()
}

/**
 * The "text" of a contract sheet, drawn at runtime: rows of rounded word shapes that cannot be
 * read. Three channels carry three masks for the sheet shader: red the word shapes, green the spans
 * that stand for personal details (they become redaction bars), blue the moment each span closes.
 */
export function contractSheetTexture(seed: number, width = 512, height = 680): CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (context === null) {
    throw new Error('2D canvas is not available')
  }
  const random = seeded(seed)
  context.fillStyle = '#000'
  context.fillRect(0, 0, width, height)
  context.globalCompositeOperation = 'lighter'

  const margin = width * 0.11
  const usable = width - margin * 2
  // Title: two thick word shapes, centred.
  context.fillStyle = 'rgb(255,0,0)'
  const titleWidths = [usable * (0.22 + random() * 0.08), usable * (0.3 + random() * 0.1)]
  let x = (width - (titleWidths[0] ?? 0) - (titleWidths[1] ?? 0) - 14) / 2
  for (const w of titleWidths) {
    roundRect(context, x, height * 0.075, w, 17)
    x += w + 14
  }

  let y = height * 0.15
  const rowHeight = 11
  const rowStep = 30
  let row = 0
  while (y < height * 0.92) {
    if (row > 0 && row % 5 === 0) {
      y += rowStep * 0.6 // paragraph gap
    }
    const lineLength = usable * (row % 5 === 4 ? 0.45 + random() * 0.25 : 0.82 + random() * 0.18)
    // A personal-detail span in some rows.
    const redactRow = random() < 0.42
    const stagger = random()
    let cursor = margin
    let wordIndex = 0
    const redactAt = redactRow ? 1 + Math.floor(random() * 3) : -1
    while (cursor < margin + lineLength - 14) {
      const w = Math.min(14 + random() * 62, margin + lineLength - cursor)
      context.fillStyle = 'rgb(255,0,0)'
      roundRect(context, cursor, y, w, rowHeight)
      if (wordIndex === redactAt) {
        // Green: the bar, a little larger than the word. Blue: when this span closes.
        const blue = Math.round(
          Math.min(1, stagger * 0.8 + (cursor / width) * 0.2) * 255,
        ).toString()
        context.fillStyle = `rgb(0,255,${blue})`
        roundRect(context, cursor - 4, y - 5, w + 8, rowHeight + 10)
      }
      cursor += w + 9
      wordIndex += 1
    }
    y += rowStep
    row += 1
  }
  // Signature lines at the foot.
  context.fillStyle = 'rgb(255,0,0)'
  roundRect(context, margin, height * 0.955, usable * 0.32, 4)
  roundRect(context, width - margin - usable * 0.32, height * 0.955, usable * 0.32, 4)

  const texture = new CanvasTexture(canvas)
  texture.colorSpace = NoColorSpace
  texture.minFilter = LinearMipmapLinearFilter
  texture.anisotropy = 4
  return texture
}

/** A soft white radial glow, for additive sprites. */
export function glowTexture(size = 128): CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const context = canvas.getContext('2d')
  if (context === null) {
    throw new Error('2D canvas is not available')
  }
  const gradient = context.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
  gradient.addColorStop(0, 'rgba(255,255,255,1)')
  gradient.addColorStop(0.25, 'rgba(255,255,255,0.45)')
  gradient.addColorStop(0.6, 'rgba(255,255,255,0.08)')
  gradient.addColorStop(1, 'rgba(255,255,255,0)')
  context.fillStyle = gradient
  context.fillRect(0, 0, size, size)
  const texture = new CanvasTexture(canvas)
  texture.colorSpace = SRGBColorSpace
  return texture
}
