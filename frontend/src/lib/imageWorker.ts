/// <reference lib="webworker" />
import { fitWithin, nextQuality } from './image'

interface Job {
  file: File
  maxEdge: number
  maxBytes: number
}

self.onmessage = (event: MessageEvent<Job>) => {
  void compress(event.data).then(
    (blob) => {
      self.postMessage({ blob })
    },
    () => {
      self.postMessage({ error: 'compress-failed' })
    },
  )
}

async function compress({ file, maxEdge }: Job): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const { width, height } = fitWithin(bitmap.width, bitmap.height, maxEdge)
  const canvas = new OffscreenCanvas(width, height)
  const context = canvas.getContext('2d')
  if (context === null) {
    throw new Error('no-2d-context')
  }
  context.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()
  let quality = 0.85
  let blob = await canvas.convertToBlob({ type: 'image/jpeg', quality })
  for (
    let next = nextQuality(quality, blob.size);
    next !== null;
    next = nextQuality(quality, blob.size)
  ) {
    quality = next
    blob = await canvas.convertToBlob({ type: 'image/jpeg', quality })
  }
  return blob
}
