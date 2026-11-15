import { Canvas, useFrame, type ThreeEvent } from "@react-three/fiber";
import { Float } from "@react-three/drei";
import {
  Component,
  Suspense,
  useEffect,
  forwardRef,
  useRef,
  useState,
  type ErrorInfo,
  type ReactNode,
  type Ref,
} from "react";
import * as THREE from "three";

import { ScenePoster } from "@/components/scene/ScenePoster";

export interface ContractSceneProps {
  alt: string;
  isActive: boolean;
  onReady: () => void;
  onFailure: () => void;
}

interface ScenePalette {
  paper: string;
  verified: string;
  uncertain: string;
  rejected: string;
  ink: string;
}

interface SceneErrorBoundaryProps {
  children: ReactNode;
  onFailure: () => void;
}

interface SceneErrorBoundaryState {
  hasError: boolean;
}

class SceneErrorBoundary extends Component<SceneErrorBoundaryProps, SceneErrorBoundaryState> {
  override state: SceneErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): SceneErrorBoundaryState {
    return { hasError: true };
  }

  override componentDidCatch(_error: Error, _errorInfo: ErrorInfo) {
    this.props.onFailure();
  }

  override render() {
    return this.state.hasError ? null : this.props.children;
  }
}

function cssTokenToHex(tokenName: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(tokenName).trim();
  const canvas = document.createElement("canvas");
  canvas.width = 1;
  canvas.height = 1;
  const context = canvas.getContext("2d", { willReadFrequently: true });

  if (!context) return "#ffffff";

  context.fillStyle = value;
  context.fillRect(0, 0, 1, 1);
  const pixel = context.getImageData(0, 0, 1, 1).data;
  return `#${[pixel[0], pixel[1], pixel[2]]
    .map((channel) =>
      Math.round(channel ?? 255)
        .toString(16)
        .padStart(2, "0"),
    )
    .join("")}`;
}

function readScenePalette(): ScenePalette {
  return {
    paper: cssTokenToHex("--foreground"),
    verified: cssTokenToHex("--verified"),
    uncertain: cssTokenToHex("--uncertain"),
    rejected: cssTokenToHex("--rejected"),
    ink: cssTokenToHex("--background"),
  };
}

export default function ContractScene({ alt, isActive, onReady, onFailure }: ContractSceneProps) {
  const [palette, setPalette] = useState<ScenePalette | null>(null);
  const [sceneReady, setSceneReady] = useState(false);

  useEffect(() => {
    setPalette(readScenePalette());
  }, []);

  const markReady = () => {
    setSceneReady(true);
    onReady();
  };

  return (
    <div
      className="relative size-full overflow-hidden rounded-2xl border border-border/70 bg-background/40 shadow-glass-panel"
      aria-busy={isActive && !sceneReady}
      data-scene-ready={sceneReady ? "true" : undefined}
    >
      <ScenePoster
        alt={alt}
        className={`absolute inset-0 size-full object-cover transition-opacity duration-500 ${sceneReady ? "opacity-0" : "opacity-100"}`}
      />
      {isActive && palette !== null && (
        <SceneErrorBoundary onFailure={onFailure}>
          <Canvas
            dpr={[1, 1.35]}
            camera={{ position: [0, 0, 8.6], fov: 40 }}
            gl={{ alpha: true, antialias: true, powerPreference: "low-power" }}
            onCreated={({ gl }) => {
              gl.setClearColor(palette.ink, 0);
            }}
            fallback={<ScenePoster alt={alt} className="size-full object-cover" />}
            aria-hidden="true"
            style={{ position: "absolute", inset: 0, touchAction: "pan-y" }}
          >
            <Suspense fallback={null}>
              <color attach="background" args={[palette.ink]} />
              <ambientLight intensity={1.6} />
              <directionalLight position={[-4, 5, 6]} intensity={2.5} color={palette.paper} />
              <pointLight
                position={[3, 1, 2]}
                intensity={8}
                distance={9}
                color={palette.verified}
              />
              <pointLight
                position={[-3, -3, 1]}
                intensity={3}
                distance={7}
                color={palette.uncertain}
              />
              <ContractArrangement palette={palette} onFirstFrame={markReady} />
            </Suspense>
          </Canvas>
        </SceneErrorBoundary>
      )}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-background/60 to-transparent"
      />
    </div>
  );
}

function ContractArrangement({
  palette,
  onFirstFrame,
}: {
  palette: ScenePalette;
  onFirstFrame: () => void;
}) {
  const rig = useRef<THREE.Group>(null);
  const beam = useRef<THREE.Mesh>(null);
  const brokenCitation = useRef<THREE.Group>(null);
  const firstFrameReported = useRef(false);
  const seal = useRef<THREE.Group>(null);

  useFrame(({ clock, pointer }, delta) => {
    if (!firstFrameReported.current) {
      firstFrameReported.current = true;
      onFirstFrame();
    }

    const group = rig.current;
    if (group) {
      const ease = 1 - Math.exp(-3 * Math.min(delta, 0.05));
      group.rotation.y = THREE.MathUtils.damp(
        group.rotation.y,
        pointer.x * 0.12 + Math.sin(clock.elapsedTime * 0.22) * 0.012,
        3,
        delta,
      );
      group.rotation.x = THREE.MathUtils.damp(
        group.rotation.x,
        -pointer.y * 0.07 + Math.sin(clock.elapsedTime * 0.31) * 0.009,
        3,
        delta,
      );
      group.position.y = Math.sin(clock.elapsedTime * 0.45) * 0.045;
      group.rotation.x += Math.sin(clock.elapsedTime * 0.35) * 0.004 * ease;
    }

    if (beam.current) {
      beam.current.position.y = Math.sin(clock.elapsedTime * 0.38) * 1.7;
      const material = beam.current.material;
      if (material instanceof THREE.Material && "opacity" in material) {
        material.opacity = 0.08 + (Math.sin(clock.elapsedTime * 0.7) + 1) * 0.035;
      }
    }

    if (brokenCitation.current) {
      brokenCitation.current.position.x = 2.12 + Math.sin(clock.elapsedTime * 0.16) * 0.14;
      brokenCitation.current.position.y = 1.18 + Math.sin(clock.elapsedTime * 0.28) * 0.07;
      brokenCitation.current.rotation.z = Math.sin(clock.elapsedTime * 0.35) * 0.025;
    }

    if (seal.current) {
      const stamp = Math.max(0, 1 - Math.abs(Math.sin(clock.elapsedTime * 0.22)) * 3.5);
      seal.current.scale.setScalar(0.88 + stamp * 0.16);
      seal.current.position.z = 0.97 + stamp * 0.16;
    }
  });

  const stopPointerEvent = (event: ThreeEvent<PointerEvent>) => event.stopPropagation();

  return (
    <group ref={rig} onPointerOver={stopPointerEvent}>
      <group rotation={[-0.13, -0.12, -0.025]}>
        <mesh position={[0, 0, -0.2]} castShadow receiveShadow>
          <boxGeometry args={[3.35, 4.55, 0.08]} />
          <meshPhysicalMaterial
            color={palette.paper}
            transparent
            opacity={0.79}
            roughness={0.38}
            metalness={0.035}
            clearcoat={0.4}
          />
        </mesh>
        <mesh position={[0, 0, -0.15]}>
          <planeGeometry args={[3.2, 4.4]} />
          <meshBasicMaterial color={palette.paper} transparent opacity={0.055} />
        </mesh>
        <DocumentRuledLines color={palette.ink} />
        <mesh ref={beam} position={[0, 0, 0.025]} rotation={[0, 0, -0.45]}>
          <boxGeometry args={[4, 0.095, 0.012]} />
          <meshBasicMaterial color={palette.verified} transparent opacity={0.1} />
        </mesh>
      </group>

      <Float speed={0.42} floatIntensity={0.16} rotationIntensity={0.035}>
        <ClauseCard position={[-1.75, 0.98, 0.54]} rotation={0.085} color={palette.verified} />
      </Float>
      <Float speed={0.32} floatIntensity={0.12} rotationIntensity={0.025}>
        <ClauseCard position={[-1.56, -0.05, 0.76]} rotation={-0.035} color={palette.verified} />
      </Float>
      <Float speed={0.37} floatIntensity={0.12} rotationIntensity={0.03}>
        <ClauseCard position={[-1.25, -1.03, 0.54]} rotation={0.055} color={palette.uncertain} />
      </Float>
      <VerificationSeal ref={seal} position={[-0.15, 0.08, 1.03]} color={palette.verified} />
      <BrokenCitation palette={palette} groupRef={brokenCitation} />
    </group>
  );
}

function DocumentRuledLines({ color }: { color: string }) {
  const rows = [1.56, 1.24, 0.91, 0.58, 0.25, -0.4, -0.73, -1.06, -1.39, -1.72];

  return (
    <group position={[0, 0, -0.144]}>
      {rows.map((y, index) => (
        <mesh key={y} position={[index % 3 === 0 ? 0.15 : -0.02, y, 0]}>
          <boxGeometry args={[index % 4 === 0 ? 2.5 : 2.86, 0.027, 0.008]} />
          <meshBasicMaterial color={color} transparent opacity={0.18} />
        </mesh>
      ))}
      {[-0.84, -1.17].map((y) => (
        <mesh key={`marker-${y}`} position={[-1.32, y, 0]}>
          <boxGeometry args={[0.1, 0.1, 0.012]} />
          <meshBasicMaterial color={color} transparent opacity={0.21} />
        </mesh>
      ))}
    </group>
  );
}

function ClauseCard({
  position,
  rotation,
  color,
}: {
  position: [number, number, number];
  rotation: number;
  color: string;
}) {
  return (
    <group position={position} rotation={[0.04, -0.07, rotation]}>
      <mesh castShadow>
        <boxGeometry args={[2.38, 0.7, 0.055]} />
        <meshPhysicalMaterial
          color={color}
          transparent
          opacity={0.24}
          roughness={0.18}
          metalness={0.08}
          transmission={0.24}
          clearcoat={0.8}
        />
      </mesh>
      <mesh position={[-0.91, 0, 0.033]}>
        <boxGeometry args={[0.12, 0.12, 0.012]} />
        <meshBasicMaterial color={color} transparent opacity={0.75} />
      </mesh>
      <mesh position={[0.11, 0.1, 0.035]}>
        <boxGeometry args={[1.56, 0.035, 0.01]} />
        <meshBasicMaterial color={color} transparent opacity={0.75} />
      </mesh>
      <mesh position={[0.02, -0.07, 0.035]}>
        <boxGeometry args={[1.39, 0.025, 0.01]} />
        <meshBasicMaterial color={color} transparent opacity={0.46} />
      </mesh>
      <mesh position={[-0.29, -0.19, 0.035]}>
        <boxGeometry args={[0.84, 0.024, 0.01]} />
        <meshBasicMaterial color={color} transparent opacity={0.34} />
      </mesh>
    </group>
  );
}

interface VerificationSealProps {
  position: [number, number, number];
  color: string;
}

const VerificationSeal = forwardRef<THREE.Group, VerificationSealProps>(function VerificationSeal(
  { position, color },
  ref,
) {
  return (
    <Float speed={0.28} floatIntensity={0.08} rotationIntensity={0.02}>
      <group ref={ref} position={position}>
        <mesh castShadow>
          <cylinderGeometry args={[0.36, 0.4, 0.1, 10]} />
          <meshPhysicalMaterial
            color={color}
            emissive={color}
            emissiveIntensity={0.24}
            roughness={0.22}
            metalness={0.44}
            clearcoat={0.8}
          />
        </mesh>
        <group position={[0, 0.19, 0]} rotation={[-0.1, 0, 0]}>
          <mesh position={[-0.07, 0.005, 0.015]} rotation={[0, 0, -0.7]}>
            <boxGeometry args={[0.075, 0.24, 0.045]} />
            <meshBasicMaterial color={color} />
          </mesh>
          <mesh position={[0.075, -0.025, 0.015]} rotation={[0, 0, 0.58]}>
            <boxGeometry args={[0.075, 0.37, 0.045]} />
            <meshBasicMaterial color={color} />
          </mesh>
        </group>
      </group>
    </Float>
  );
});

function BrokenCitation({
  palette,
  groupRef,
}: {
  palette: ScenePalette;
  groupRef: Ref<THREE.Group>;
}) {
  return (
    <group ref={groupRef} position={[2.12, 1.18, 0.36]} rotation={[0, -0.06, 0.09]}>
      <mesh position={[0, 0.1, -0.025]}>
        <boxGeometry args={[1.22, 0.63, 0.034]} />
        <meshPhysicalMaterial
          color={palette.rejected}
          transparent
          opacity={0.17}
          roughness={0.22}
        />
      </mesh>
      <mesh position={[0, 0.18, 0.004]} rotation={[0, 0, 0.12]}>
        <boxGeometry args={[0.73, 0.028, 0.012]} />
        <meshBasicMaterial color={palette.rejected} transparent opacity={0.78} />
      </mesh>
      <mesh position={[-0.09, 0.11, 0.008]} rotation={[0, 0, -0.83]}>
        <boxGeometry args={[0.045, 0.31, 0.016]} />
        <meshBasicMaterial color={palette.rejected} />
      </mesh>
      <mesh position={[0.05, -0.08, 0.008]} rotation={[0, 0, 0.7]}>
        <boxGeometry args={[0.045, 0.39, 0.016]} />
        <meshBasicMaterial color={palette.rejected} />
      </mesh>
      <mesh position={[-0.7, 0.55, 0.01]} rotation={[0, 0, 0.22]}>
        <boxGeometry args={[0.2, 0.16, 0.035]} />
        <meshPhysicalMaterial color={palette.rejected} transparent opacity={0.2} roughness={0.2} />
      </mesh>
      <mesh position={[0.73, -0.28, 0.01]} rotation={[0, 0, -0.28]}>
        <boxGeometry args={[0.17, 0.2, 0.035]} />
        <meshPhysicalMaterial color={palette.rejected} transparent opacity={0.2} roughness={0.2} />
      </mesh>
    </group>
  );
}
