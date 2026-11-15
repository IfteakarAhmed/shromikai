import { ClientOnly } from "@tanstack/react-router";
import { Suspense, lazy, useCallback, useEffect, useState } from "react";

import { ScenePoster } from "@/components/scene/ScenePoster";

const LazyContractScene = lazy(() => import("@/components/scene/ContractScene"));

interface ContractSceneFrameProps {
  alt: string;
  lowBandwidth: boolean;
}

function getWebGlAvailability(): boolean {
  try {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("webgl2") ?? canvas.getContext("webgl");
    context?.getExtension("WEBGL_lose_context")?.loseContext();
    return context !== null;
  } catch {
    return false;
  }
}

function StaticPoster({ alt }: { alt: string }) {
  return (
    <div className="relative size-full overflow-hidden rounded-2xl border border-border/70 bg-background/40 shadow-glass-panel">
      <ScenePoster alt={alt} className="absolute inset-0 size-full object-cover" />
    </div>
  );
}

export function ContractSceneFrame({ alt, lowBandwidth }: ContractSceneFrameProps) {
  const [motionAllowed, setMotionAllowed] = useState(false);
  const [webglAvailable, setWebglAvailable] = useState(false);
  const [sceneFailed, setSceneFailed] = useState(false);

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const updateMotionPreference = () => setMotionAllowed(!query.matches);

    updateMotionPreference();
    query.addEventListener("change", updateMotionPreference);
    setWebglAvailable(getWebGlAvailability());

    return () => query.removeEventListener("change", updateMotionPreference);
  }, []);

  const isActive = !lowBandwidth && motionAllowed && webglAvailable && !sceneFailed;
  const poster = <StaticPoster alt={alt} />;
  const handleSceneFailure = useCallback(() => setSceneFailed(true), []);

  return (
    <ClientOnly fallback={poster}>
      {isActive ? (
        <Suspense fallback={poster}>
          <LazyContractScene
            alt={alt}
            isActive={isActive}
            onReady={() => undefined}
            onFailure={handleSceneFailure}
          />
        </Suspense>
      ) : (
        poster
      )}
    </ClientOnly>
  );
}
