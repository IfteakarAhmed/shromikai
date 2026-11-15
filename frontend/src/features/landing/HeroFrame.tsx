import { Suspense, lazy, useEffect, useState } from 'react'
import posterUrl from '../../assets/contract-poster.webp'
import { usePreferences } from '../../state/preferencesContext'
import { useMediaQuery, useWebGl } from './useCapabilities'

// The 3D scene is a separate chunk, fetched only when it will actually be shown (ADR-0023).
const ContractScene = lazy(() => import('./ContractScene'))

/** The static picture of the scene: the fallback in every case where 3D is not shown. */
export function HeroPoster({ alt }: { alt: string }) {
  return (
    <img
      src={posterUrl}
      alt={alt}
      width={960}
      height={768}
      fetchPriority="high"
      className="size-full object-cover"
    />
  )
}

/**
 * The hero picture. It is the poster unless the browser can do WebGL, the visitor has not asked for
 * reduced motion and low-bandwidth mode is off; then a procedural 3D scene loads after the page is
 * idle and fades in over the poster. If the scene fails, the poster stays (FR-68, FR-76).
 */
export function HeroFrame({ alt }: { alt: string }) {
  const { lowBandwidth } = usePreferences()
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  const webgl = useWebGl()
  const [failed, setFailed] = useState(false)
  const [idle, setIdle] = useState(false)
  const wanted = webgl && !reducedMotion && !lowBandwidth && !failed

  useEffect(() => {
    if (!wanted) {
      return undefined
    }
    const timer = window.setTimeout(() => {
      setIdle(true)
    }, 600)
    return () => {
      window.clearTimeout(timer)
    }
  }, [wanted])

  return (
    <div
      data-hero={wanted && idle ? 'scene' : 'poster'}
      className="relative aspect-[5/4] w-full overflow-hidden rounded-2xl border border-border bg-background/40 shadow-glass"
    >
      <HeroPoster alt={alt} />
      {wanted && idle && (
        <Suspense fallback={null}>
          <ContractScene
            onFailure={() => {
              setFailed(true)
            }}
          />
        </Suspense>
      )}
    </div>
  )
}
