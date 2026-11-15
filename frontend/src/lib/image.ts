/**
 * Photos are made smaller on the device before they are sent (FR-69, NFR-10): at most 1600 px on the
 * long edge and about 1 MB each, so a phone on a slow connection can upload fifteen pages. The work
 * runs in a Web Worker with an OffscreenCanvas; where the browser has neither, the picture is sent
 * as it is and the 10 MB limit of the form still applies.
 */

export const MAX_EDGE = 1600
export const MAX_IMAGE_BYTES = 1024 * 1024
const MIN_QUALITY = 0.4

/** The size that fits within `max` on the long edge, keeping the ratio; never larger than the input. */
export function fitWithin(
  width: number,
  height: number,
  max: number,
): { width: number; height: number } {
  const longEdge = Math.max(width, height)
  if (longEdge <= max) {
    return { width, height }
  }
  const scale = max / longEdge
  return { width: Math.round(width * scale), height: Math.round(height * scale) }
}

/** The next JPEG quality to try, or null when the picture is small enough or cannot get smaller. */
export function nextQuality(quality: number, bytes: number): number | null {
  if (bytes <= MAX_IMAGE_BYTES) {
    return null
  }
  const next = Math.round((quality - 0.15) * 100) / 100
  return next < MIN_QUALITY ? null : next
}

interface WorkerReply {
  blob?: Blob
  error?: string
}

/** A smaller JPEG of a photo, or the photo itself when it cannot be processed here. */
export async function compressImage(file: File): Promise<File> {
  if (typeof Worker === 'undefined' || typeof OffscreenCanvas === 'undefined') {
    return file
  }
  try {
    const worker = new Worker(new URL('./imageWorker.ts', import.meta.url), { type: 'module' })
    const reply = await new Promise<WorkerReply>((resolve, reject) => {
      worker.onmessage = (event: MessageEvent<WorkerReply>) => {
        resolve(event.data)
      }
      worker.onerror = () => {
        reject(new Error('worker-failed'))
      }
      worker.postMessage({ file, maxEdge: MAX_EDGE, maxBytes: MAX_IMAGE_BYTES })
    }).finally(() => {
      worker.terminate()
    })
    if (reply.blob === undefined) {
      return file
    }
    const name = file.name.replace(/\.[^.]+$/, '') + '.jpg'
    return new File([reply.blob], name, { type: 'image/jpeg' })
  } catch {
    return file
  }
}
