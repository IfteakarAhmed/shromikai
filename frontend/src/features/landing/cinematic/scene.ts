import {
  AdditiveBlending,
  BufferAttribute,
  BufferGeometry,
  Color,
  CylinderGeometry,
  DoubleSide,
  Group,
  HalfFloatType,
  InstancedBufferAttribute,
  InstancedMesh,
  LineSegments,
  NormalBlending,
  Mesh,
  Object3D,
  OrthographicCamera,
  PerspectiveCamera,
  PlaneGeometry,
  Points,
  RingGeometry,
  Scene,
  ShaderMaterial,
  Sprite,
  SpriteMaterial,
  TorusGeometry,
  UnsignedByteType,
  Vector2,
  Vector3,
  WebGLRenderTarget,
  WebGLRenderer,
  type IUniform,
  type Texture,
} from 'three'
import type { Palette } from '../scene3d'
import { damp } from './damp'
import { DEFAULT_FLAGS, type DevFlags } from './devFlags'
import { frameStats } from './perfStats'
import { INITIAL_QUALITY, nextQuality, qualitySettings, type QualityState } from './quality'
import * as glsl from './shaders'
import { contractSheetTexture, glowTexture, seeded } from './textures'
import { KEYFRAMES, STAGES, beatsAt, cameraAt, ramp, type CameraPose, type Vec3 } from './timeline'

export interface CinematicHandle {
  /** The timeline value the camera should travel to (0 to 1); it is reached with damping. */
  setTarget: (t: number) => void
  /** Pointer position in -1..1, for a small parallax. */
  setPointer: (x: number, y: number) => void
  /** Start or stop the animation loop (hidden tab, page scrolled past the story). */
  setRunning: (running: boolean) => void
  /** Draw one frame at a fixed timeline value and clock, for posters; returns the canvas. */
  renderStill: (
    t: number,
    width: number,
    height: number,
    seconds?: number,
    pose?: CameraPose,
  ) => HTMLCanvasElement
  /** The current quality level (3 is full), for tests and the debug attribute. */
  quality: () => number
  dispose: () => void
}

export interface CinematicOptions {
  /** Keeps the drawing buffer so a still can be read back (poster script only). */
  preserveDrawingBuffer?: boolean
  /** Called when the GPU context is lost; the landing then falls back to the poster. */
  onContextLost?: () => void
  /** Starts the loop at once (default true). */
  autoStart?: boolean
  /** Development switches (`?bloom=off`, `?quality=3`, `?perf=1` and others); see devFlags. */
  flags?: DevFlags
}

const FULL_DUST = 2600
const FULL_SHIELD = 1500
const FULL_SHARDS = 1800
const MAX_PIXEL_RATIO = 1.5

type Uniforms = Record<string, IUniform>

/**
 * The cinematic landing scene (FR-68, ADR-0023): one camera journey through six stages, driven by
 * the scroll timeline. Everything is procedural: geometry, shaders, canvas textures and particles.
 * There is no readable text, no logo and no number in it; the HTML chapters carry the meaning. It
 * is plain three.js with named imports and a small custom bloom (bright pass, two blur sizes,
 * composite) instead of the post-processing add-ons.
 */
export function createCinematicScene(
  host: HTMLElement,
  palette: Palette,
  options: CinematicOptions = {},
): CinematicHandle {
  const renderer = new WebGLRenderer({
    antialias: false,
    alpha: false,
    powerPreference: 'high-performance',
    preserveDrawingBuffer: options.preserveDrawingBuffer ?? false,
  })
  if (!renderer.capabilities.isWebGL2) {
    renderer.dispose()
    throw new Error('the cinematic scene needs WebGL2')
  }
  const flags = options.flags ?? DEFAULT_FLAGS
  const canvas = renderer.domElement
  canvas.setAttribute('aria-hidden', 'true')
  canvas.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;display:block'
  host.appendChild(canvas)

  const colour = (hex: string) => new Color(hex)
  const ink = colour(palette.ink)
  const paper = colour(palette.paper)
  const verified = colour(palette.verified)
  const amber = colour(palette.uncertain)
  const coral = colour(palette.rejected)
  renderer.setClearColor(ink, 1)

  const scene = new Scene()
  const camera = new PerspectiveCamera(38, 1, 0.1, 80)

  const disposables: { dispose: () => void }[] = []
  const track = <T extends { dispose: () => void }>(item: T): T => {
    disposables.push(item)
    return item
  }
  const time: IUniform<number> = { value: 0 }
  const pixelRatio: IUniform<number> = { value: 1 }

  function material(
    vertexShader: string,
    fragmentShader: string,
    uniforms: Uniforms,
    additive = false,
  ): ShaderMaterial {
    return track(
      new ShaderMaterial({
        vertexShader,
        fragmentShader,
        uniforms: { uTime: time, uPixelRatio: pixelRatio, ...uniforms },
        transparent: true,
        depthWrite: false,
        side: DoubleSide,
        blending: additive ? AdditiveBlending : NormalBlending,
      }),
    )
  }
  const glow = track(glowTexture())
  const sheetMeshes: Mesh[] = []
  const shaftMeshes: Mesh[] = []
  const textures: Texture[] = [1, 2, 3].map((seed) => track(contractSheetTexture(seed * 7919)))

  // ---- Stage 1 and 2: glass contract sheets, light shafts, the shield ring ----
  const redact: IUniform<number> = { value: 0 }
  // The opening sheets step aside while the camera looks at the side stages, and come back.
  const heroFade: IUniform<number> = { value: 1 }
  const hero = new Group()
  hero.position.set(...STAGES.hero)
  scene.add(hero)

  function sheet(
    width: number,
    height: number,
    map: Texture,
    rim: Color,
    opacity: number,
    seed: number,
    fade: IUniform<number> = { value: 1 },
  ): Mesh {
    const mesh = new Mesh(
      track(new PlaneGeometry(width, height, 12, 16)),
      material(glsl.sheetVertex, glsl.sheetFragment, {
        uMap: { value: map },
        uPaper: { value: paper },
        uInk: { value: ink },
        uRim: { value: rim },
        uSweep: { value: verified },
        uOpacity: { value: opacity },
        uRedact: redact,
        uFade: fade,
        uSeed: { value: seed },
        uBend: { value: 0.08 },
      }),
    )
    sheetMeshes.push(mesh)
    return mesh
  }

  const sheets: { mesh: Mesh; base: Vec3; rotation: Vec3; phase: number }[] = []
  const sheetLayout: { at: Vec3; rotation: Vec3; size: [number, number]; opacity: number }[] = [
    { at: [0.2, 1.9, -3.2], rotation: [0.12, -0.18, -0.12], size: [2.5, 3.3], opacity: 0.05 },
    { at: [-2.1, 0.8, -1.7], rotation: [0.05, -0.12, -0.08], size: [2.6, 3.5], opacity: 0.06 },
    { at: [2.3, -0.8, -1.3], rotation: [-0.1, -0.42, 0.1], size: [2.6, 3.5], opacity: 0.06 },
    { at: [0, 0, 0.6], rotation: [-0.08, -0.3, 0.05], size: [3.0, 4.0], opacity: 0.09 },
  ]
  sheetLayout.forEach((layout, index) => {
    const mesh = sheet(
      layout.size[0],
      layout.size[1],
      textures[index % textures.length] ?? glow,
      index === sheetLayout.length - 1 ? verified.clone().lerp(paper, 0.5) : paper,
      layout.opacity,
      index * 1.7,
      heroFade,
    )
    mesh.position.set(...layout.at)
    mesh.rotation.set(...layout.rotation)
    hero.add(mesh)
    sheets.push({ mesh, base: layout.at, rotation: layout.rotation, phase: index * 1.3 })
  })
  const front = sheets[sheets.length - 1]

  const shafts: ShaderMaterial[] = []
  ;[
    { at: [-0.6, 4.5, -3] as Vec3, angle: -0.38, width: 2.2, seed: 1 },
    { at: [1.6, 5, -4] as Vec3, angle: -0.3, width: 3.2, seed: 2 },
    { at: [3.4, 4.6, -2.5] as Vec3, angle: -0.46, width: 1.6, seed: 3 },
  ].forEach(({ at, angle, width, seed }) => {
    const shaftMaterial = material(
      glsl.shaftVertex,
      glsl.shaftFragment,
      {
        uColour: { value: paper.clone().lerp(verified, 0.25) },
        uSeed: { value: seed },
        uIntensity: { value: 0.11 },
      },
      true,
    )
    shafts.push(shaftMaterial)
    const shaft = new Mesh(track(new PlaneGeometry(width, 15)), shaftMaterial)
    shaft.position.set(...at)
    shaft.rotation.set(0, 0, angle)
    shaftMeshes.push(shaft)
    hero.add(shaft)
  })

  // The shield: two rings of particles around the front sheet, gathered from a loose cloud.
  const shieldProgress: IUniform<number> = { value: 0 }
  const shield = (() => {
    const random = seeded(41)
    const positions = new Float32Array(FULL_SHIELD * 3)
    const starts = new Float32Array(FULL_SHIELD * 3)
    const delays = new Float32Array(FULL_SHIELD)
    for (let i = 0; i < FULL_SHIELD; i += 1) {
      const angle = random() * Math.PI * 2
      const radius = (i % 3 === 0 ? 2.78 : 2.58) + (random() - 0.5) * 0.07
      positions.set(
        [Math.cos(angle) * radius, Math.sin(angle) * radius, (random() - 0.5) * 0.08],
        i * 3,
      )
      const u = random() * Math.PI * 2
      const v = Math.acos(2 * random() - 1)
      const r = 4 + random() * 4
      starts.set(
        [r * Math.sin(v) * Math.cos(u), r * Math.sin(v) * Math.sin(u), r * Math.cos(v)],
        i * 3,
      )
      delays[i] = random()
    }
    const geometry = track(new BufferGeometry())
    geometry.setAttribute('position', new BufferAttribute(positions, 3))
    geometry.setAttribute('aStart', new BufferAttribute(starts, 3))
    geometry.setAttribute('aDelay', new BufferAttribute(delays, 1))
    const points = new Points(
      geometry,
      material(
        glsl.shieldVertex,
        glsl.shieldFragment,
        {
          uShield: shieldProgress,
          uSize: { value: 5.5 },
          uColour: { value: verified },
          uFade: heroFade,
        },
        true,
      ),
    )
    points.frustumCulled = false
    return points
  })()
  if (front !== undefined) {
    shield.position.set(...front.base)
    hero.add(shield)
  }

  // ---- Stage 3: the lattice of law-section cards ----
  const search: IUniform<number> = { value: 0 }
  const latticeFade: IUniform<number> = { value: 0 }
  const lattice = new Group()
  lattice.position.set(...STAGES.lattice)
  scene.add(lattice)
  const latticeCamera = new Vector3(...(KEYFRAMES[2]?.position ?? [0, 0, 10]))
  const nodes = (() => {
    const random = seeded(97)
    const list: Vector3[] = []
    const columns = 12
    const rows = 7
    for (let c = 0; c < columns; c += 1) {
      for (let r = 0; r < rows; r += 1) {
        const x = (c - (columns - 1) / 2) * 2.1 + (random() - 0.5) * 0.7
        const y = (r - (rows - 1) / 2) * 1.6 + (random() - 0.5) * 0.5
        const z = (random() - 0.5) * 3 - x * x * 0.02
        list.push(new Vector3(x, y, z))
      }
    }
    return list
  })()
  // Edges: each card to its two nearest neighbours.
  const edges: [number, number][] = []
  const edgeKey = new Set<string>()
  nodes.forEach((node, i) => {
    nodes
      .map((other, j) => ({ j, d: node.distanceTo(other) }))
      .filter(({ j }) => j !== i)
      .sort((a, b) => a.d - b.d)
      .slice(0, 2)
      .forEach(({ j }) => {
        const key = i < j ? `${i}-${j}` : `${j}-${i}`
        if (!edgeKey.has(key)) {
          edgeKey.add(key)
          edges.push([i, j])
        }
      })
  })
  // A search tree from the card nearest the camera; pulses run away from it.
  const localCamera = latticeCamera.clone().sub(lattice.position)
  const hub = nodes.reduce(
    (best, node, i) =>
      node.distanceTo(localCamera) < (nodes[best]?.distanceTo(localCamera) ?? Infinity) ? i : best,
    0,
  )
  const parent = new Map<number, number>([[hub, -1]])
  const depth = new Map<number, number>([[hub, 0]])
  const queue = [hub]
  while (queue.length > 0) {
    const at = queue.shift() ?? hub
    for (const [a, b] of edges) {
      const next = a === at ? b : b === at ? a : -1
      if (next >= 0 && !parent.has(next)) {
        parent.set(next, at)
        depth.set(next, (depth.get(at) ?? 0) + 1)
        queue.push(next)
      }
    }
  }
  const byDepth = [...depth.entries()].sort((a, b) => b[1] - a[1]).map(([i]) => i)
  const matched = new Set(byDepth.filter((_, k) => k % 7 === 2).slice(0, 3))
  const amberNode = byDepth[Math.floor(byDepth.length / 2)] ?? -1
  const hotEdges = new Set<string>()
  for (const target of matched) {
    let at = target
    while ((parent.get(at) ?? -1) >= 0) {
      const from = parent.get(at) ?? -1
      hotEdges.add(`${from}>${at}`)
      at = from
    }
  }

  const cards = (() => {
    const count = nodes.length
    const mesh = new InstancedMesh(
      track(new PlaneGeometry(1.35, 0.86)),
      material(glsl.latticeVertex, glsl.latticeFragment, {
        uPaper: { value: paper },
        uVerified: { value: verified },
        uAmber: { value: amber },
        uSearch: search,
        uFade: latticeFade,
      }),
      count,
    )
    const matchAttribute = new Float32Array(count)
    const amberAttribute = new Float32Array(count)
    const seedAttribute = new Float32Array(count)
    const holder = new Object3D()
    const random = seeded(5)
    nodes.forEach((node, i) => {
      holder.position.copy(node)
      holder.lookAt(localCamera)
      holder.rotateZ((random() - 0.5) * 0.12)
      holder.updateMatrix()
      mesh.setMatrixAt(i, holder.matrix)
      matchAttribute[i] = matched.has(i) ? 1 : 0
      amberAttribute[i] = i === amberNode ? 1 : 0
      seedAttribute[i] = random()
    })
    mesh.geometry.setAttribute('aMatch', new InstancedBufferAttribute(matchAttribute, 1))
    mesh.geometry.setAttribute('aAmber', new InstancedBufferAttribute(amberAttribute, 1))
    mesh.geometry.setAttribute('aSeed', new InstancedBufferAttribute(seedAttribute, 1))
    mesh.frustumCulled = false
    return mesh
  })()
  lattice.add(cards)

  const pulseStarts: number[] = []
  const pulseEnds: number[] = []
  const pulseOffsets: number[] = []
  const pulseHot: number[] = []
  const linePositions: number[] = []
  const lineT: number[] = []
  const lineHot: number[] = []
  const lineOffset: number[] = []
  const random = seeded(23)
  for (const [a, b] of edges) {
    // Orient the edge away from the hub when it is on the search tree.
    const forward = parent.get(b) === a
    const backward = parent.get(a) === b
    const from = backward ? b : a
    const to = backward ? a : b
    const start = nodes[from]
    const end = nodes[to]
    if (start === undefined || end === undefined) {
      continue
    }
    const hot = hotEdges.has(`${from}>${to}`) ? 1 : 0
    const offset = random()
    linePositions.push(start.x, start.y, start.z, end.x, end.y, end.z)
    lineT.push(0, 1)
    lineHot.push(hot, hot)
    lineOffset.push(offset, offset)
    if (forward || backward) {
      const pulses = hot === 1 ? 3 : 1
      for (let k = 0; k < pulses; k += 1) {
        pulseStarts.push(start.x, start.y, start.z)
        pulseEnds.push(end.x, end.y, end.z)
        pulseOffsets.push((offset + k / pulses) % 1)
        pulseHot.push(hot)
      }
    }
  }
  const lineGeometry = track(new BufferGeometry())
  lineGeometry.setAttribute('position', new BufferAttribute(new Float32Array(linePositions), 3))
  lineGeometry.setAttribute('aT', new BufferAttribute(new Float32Array(lineT), 1))
  lineGeometry.setAttribute('aHot', new BufferAttribute(new Float32Array(lineHot), 1))
  lineGeometry.setAttribute('aOffset', new BufferAttribute(new Float32Array(lineOffset), 1))
  const lines = new LineSegments(
    lineGeometry,
    material(
      glsl.edgeVertex,
      glsl.edgeFragment,
      {
        uPaper: { value: paper },
        uVerified: { value: verified },
        uSearch: search,
        uFade: latticeFade,
      },
      true,
    ),
  )
  lines.frustumCulled = false
  lattice.add(lines)
  const pulseGeometry = track(new BufferGeometry())
  pulseGeometry.setAttribute('position', new BufferAttribute(new Float32Array(pulseEnds), 3))
  pulseGeometry.setAttribute('aStart', new BufferAttribute(new Float32Array(pulseStarts), 3))
  pulseGeometry.setAttribute('aOffset', new BufferAttribute(new Float32Array(pulseOffsets), 1))
  pulseGeometry.setAttribute('aHot', new BufferAttribute(new Float32Array(pulseHot), 1))
  const pulses = new Points(
    pulseGeometry,
    material(
      glsl.pulseVertex,
      glsl.pulseFragment,
      { uSearch: search, uSize: { value: 9 }, uHot: { value: verified }, uCold: { value: paper } },
      true,
    ),
  )
  pulses.frustumCulled = false
  lattice.add(pulses)

  // ---- Stage 4: three check rings lock, the seal stamps with a shockwave ----
  const verify = new Group()
  verify.position.set(...STAGES.verify)
  verify.lookAt(new Vector3(...(KEYFRAMES[3]?.position ?? [0, 0, 10])))
  scene.add(verify)
  const verifySheet = sheet(2.1, 2.8, textures[1] ?? glow, paper, 0.1, 3.1)
  verify.add(verifySheet)
  const dim = paper.clone().lerp(ink, 0.55)
  const rings = [1.7, 1.98, 2.26].map((radius) => {
    const ringMaterial = material(
      glsl.flatVertex,
      glsl.flatFragment,
      { uColour: { value: dim.clone() }, uOpacity: { value: 0.5 } },
      true,
    )
    const ring = new Mesh(track(new TorusGeometry(radius, 0.022, 8, 160)), ringMaterial)
    verify.add(ring)
    return { ring, material: ringMaterial }
  })
  const seal = new Group()
  seal.position.set(0.55, -0.7, 0.35)
  verify.add(seal)
  const sealMaterial = material(glsl.flatVertex, glsl.flatFragment, {
    uColour: { value: verified },
    uOpacity: { value: 0 },
  })
  const disc = new Mesh(track(new CylinderGeometry(0.62, 0.62, 0.06, 72)), sealMaterial)
  disc.rotation.x = Math.PI / 2
  seal.add(disc)
  const markMaterial = material(glsl.flatVertex, glsl.flatFragment, {
    uColour: { value: ink },
    uOpacity: { value: 0 },
  })
  const innerRing = new Mesh(track(new RingGeometry(0.47, 0.52, 72)), markMaterial)
  innerRing.position.z = 0.04
  seal.add(innerRing)
  const tickShort = new Mesh(track(new PlaneGeometry(0.09, 0.26)), markMaterial)
  tickShort.position.set(-0.09, -0.03, 0.045)
  tickShort.rotation.z = 0.75
  const tickLong = new Mesh(track(new PlaneGeometry(0.09, 0.46)), markMaterial)
  tickLong.position.set(0.07, 0.04, 0.045)
  tickLong.rotation.z = -0.62
  seal.add(tickShort, tickLong)
  const sealGlow = new Sprite(
    track(
      new SpriteMaterial({
        map: glow,
        color: verified,
        blending: AdditiveBlending,
        transparent: true,
        depthWrite: false,
        opacity: 0,
      }),
    ),
  )
  sealGlow.position.z = -0.05
  seal.add(sealGlow)
  const shockMaterial = material(
    glsl.flatVertex,
    glsl.flatFragment,
    { uColour: { value: verified }, uOpacity: { value: 0 } },
    true,
  )
  const shock = new Mesh(track(new RingGeometry(0.92, 1, 96)), shockMaterial)
  shock.position.z = 0.02
  seal.add(shock)

  // ---- Stage 5: the coral card cracks and disperses; the emerald card stays ----
  const crack: IUniform<number> = { value: 0 }
  const dissolve: IUniform<number> = { value: 0 }
  const invented = new Group()
  invented.position.set(...STAGES.invented)
  invented.lookAt(new Vector3(...(KEYFRAMES[4]?.position ?? [0, 0, 10])))
  scene.add(invented)
  const steady = sheet(2.0, 2.7, textures[2] ?? glow, verified, 0.12, 5.3)
  steady.position.set(-1.3, 0, 0)
  steady.rotation.y = 0.16
  invented.add(steady)
  const steadyGlow = new Sprite(
    track(
      new SpriteMaterial({
        map: glow,
        color: verified,
        blending: AdditiveBlending,
        transparent: true,
        depthWrite: false,
        opacity: 0.35,
      }),
    ),
  )
  steadyGlow.scale.set(4.2, 4.6, 1)
  steadyGlow.position.set(-1.3, 0, -0.3)
  invented.add(steadyGlow)
  const coralCard = new Group()
  coralCard.position.set(1.3, 0, 0)
  coralCard.rotation.y = -0.16
  invented.add(coralCard)
  coralCard.add(
    new Mesh(
      track(new PlaneGeometry(2.0, 2.7, 1, 1)),
      material(glsl.sheetVertex, glsl.crackFragment, {
        uMap: { value: textures[0] ?? glow },
        uCoral: { value: coral },
        uPaper: { value: paper },
        uCrack: crack,
        uDissolve: dissolve,
        uFade: { value: 1 },
        uSeed: { value: 0 },
        uBend: { value: 0 },
      }),
    ),
  )
  const shards = (() => {
    const shardRandom = seeded(77)
    const positions = new Float32Array(FULL_SHARDS * 3)
    const uvs = new Float32Array(FULL_SHARDS * 2)
    const directions = new Float32Array(FULL_SHARDS * 3)
    const phases = new Float32Array(FULL_SHARDS)
    for (let i = 0; i < FULL_SHARDS; i += 1) {
      const u = shardRandom()
      const v = shardRandom()
      uvs.set([u, v], i * 2)
      positions.set([(u - 0.5) * 2.0, (v - 0.5) * 2.7, 0], i * 3)
      const outward = new Vector3(
        u - 0.5 + (shardRandom() - 0.3) * 0.8,
        v - 0.5 + (shardRandom() - 0.5) * 0.6,
        0.4 + shardRandom(),
      )
      outward.normalize()
      directions.set([outward.x, outward.y, outward.z], i * 3)
      phases[i] = shardRandom() * 6.28
    }
    const geometry = track(new BufferGeometry())
    geometry.setAttribute('position', new BufferAttribute(positions, 3))
    geometry.setAttribute('aUv', new BufferAttribute(uvs, 2))
    geometry.setAttribute('aDirection', new BufferAttribute(directions, 3))
    geometry.setAttribute('aPhase', new BufferAttribute(phases, 1))
    const points = new Points(
      geometry,
      material(
        glsl.shardVertex,
        glsl.shardFragment,
        {
          uDissolve: dissolve,
          uSize: { value: 6 },
          uColour: { value: coral },
          uHotColour: { value: coral.clone().lerp(paper, 0.5) },
        },
        true,
      ),
    )
    points.frustumCulled = false
    return points
  })()
  coralCard.add(shards)

  // ---- Dust through the whole journey ----
  const dust = (() => {
    const dustRandom = seeded(11)
    const positions = new Float32Array(FULL_DUST * 3)
    const scales = new Float32Array(FULL_DUST)
    const phases = new Float32Array(FULL_DUST)
    const speeds = new Float32Array(FULL_DUST)
    for (let i = 0; i < FULL_DUST; i += 1) {
      positions.set(
        [(dustRandom() - 0.5) * 26, (dustRandom() - 0.5) * 13 + 0.5, dustRandom() * 32 - 18],
        i * 3,
      )
      scales[i] = 0.4 + dustRandom() ** 3 * 2.2
      phases[i] = dustRandom() * 6.28
      speeds[i] = 0.5 + dustRandom()
    }
    const geometry = track(new BufferGeometry())
    geometry.setAttribute('position', new BufferAttribute(positions, 3))
    geometry.setAttribute('aScale', new BufferAttribute(scales, 1))
    geometry.setAttribute('aPhase', new BufferAttribute(phases, 1))
    geometry.setAttribute('aSpeed', new BufferAttribute(speeds, 1))
    const points = new Points(
      geometry,
      material(
        glsl.dustVertex,
        glsl.dustFragment,
        {
          uSize: { value: 4.2 },
          uFade: { value: 0.85 },
          uColour: { value: paper.clone().lerp(verified, 0.15) },
        },
        true,
      ),
    )
    points.frustumCulled = false
    return points
  })()
  scene.add(dust)

  // ---- Bloom: bright pass, blur at half and quarter size, composite ----
  const floatTargets = renderer.extensions.has('EXT_color_buffer_float')
  const targetType = floatTargets ? HalfFloatType : UnsignedByteType
  const sceneTarget = track(new WebGLRenderTarget(1, 1, { type: targetType, samples: 4 }))
  const half = [0, 1].map(() => track(new WebGLRenderTarget(1, 1, { type: targetType })))
  const quarter = [0, 1].map(() => track(new WebGLRenderTarget(1, 1, { type: targetType })))
  const screenCamera = new OrthographicCamera(-1, 1, 1, -1, 0, 1)
  const screenGeometry = track(new PlaneGeometry(2, 2))
  const pass = (fragmentShader: string, uniforms: Uniforms) => {
    const passMaterial = track(
      new ShaderMaterial({
        vertexShader: glsl.screenVertex,
        fragmentShader,
        uniforms,
        depthTest: false,
        depthWrite: false,
      }),
    )
    const passScene = new Scene()
    passScene.add(new Mesh(screenGeometry, passMaterial))
    return { scene: passScene, material: passMaterial }
  }
  const bright = pass(glsl.brightFragment, { uScene: { value: null }, uThreshold: { value: 0.72 } })
  const blur = pass(glsl.blurFragment, {
    uInput: { value: null },
    uDirection: { value: new Vector2() },
  })
  const composite = pass(glsl.compositeFragment, {
    uScene: { value: null },
    uBloom: { value: null },
    uBloomWide: { value: null },
    uStrength: { value: 0.75 },
  })

  function runBlur(targets: WebGLRenderTarget[], spread: number) {
    const [a, b] = targets
    if (a === undefined || b === undefined) {
      return
    }
    const direction = blur.material.uniforms['uDirection']?.value as Vector2
    const input = blur.material.uniforms['uInput']
    if (input === undefined) {
      return
    }
    for (let i = 0; i < 2; i += 1) {
      input.value = a.texture
      direction.set(spread / a.width, 0)
      renderer.setRenderTarget(b)
      renderer.render(blur.scene, screenCamera)
      input.value = b.texture
      direction.set(0, spread / a.height)
      renderer.setRenderTarget(a)
      renderer.render(blur.scene, screenCamera)
    }
  }

  function draw(useBloom: boolean) {
    if (!useBloom) {
      renderer.setRenderTarget(null)
      renderer.render(scene, camera)
      return
    }
    const [halfA] = half
    const [quarterA] = quarter
    if (halfA === undefined || quarterA === undefined) {
      return
    }
    renderer.setRenderTarget(sceneTarget)
    renderer.render(scene, camera)
    const brightUniforms = bright.material.uniforms
    if (brightUniforms['uScene']) {
      brightUniforms['uScene'].value = sceneTarget.texture
    }
    renderer.setRenderTarget(halfA)
    renderer.render(bright.scene, screenCamera)
    if (brightUniforms['uScene']) {
      brightUniforms['uScene'].value = halfA.texture
    }
    renderer.setRenderTarget(quarterA)
    renderer.render(bright.scene, screenCamera)
    runBlur(half, 1.2)
    runBlur(quarter, 1.6)
    const compositeUniforms = composite.material.uniforms
    if (
      compositeUniforms['uScene'] &&
      compositeUniforms['uBloom'] &&
      compositeUniforms['uBloomWide']
    ) {
      compositeUniforms['uScene'].value = sceneTarget.texture
      compositeUniforms['uBloom'].value = halfA.texture
      compositeUniforms['uBloomWide'].value = quarterA.texture
    }
    renderer.setRenderTarget(null)
    renderer.render(composite.scene, screenCamera)
  }

  // ---- State, sizing, quality ----
  let quality: QualityState =
    flags.quality === null ? INITIAL_QUALITY : { ...INITIAL_QUALITY, level: flags.quality }
  let settings = qualitySettings(quality.level)
  let width = 1
  let height = 1
  let targetT = 0
  let currentT = 0
  let reported = -1
  const pointer = { x: 0, y: 0, dx: 0, dy: 0 }

  function applyQuality() {
    pixelRatio.value = Math.min(
      window.devicePixelRatio || 1,
      settings.maxPixelRatio,
      MAX_PIXEL_RATIO,
    )
    renderer.setPixelRatio(pixelRatio.value)
    dust.geometry.setDrawRange(0, Math.floor(FULL_DUST * settings.particles))
    shield.geometry.setDrawRange(0, Math.floor(FULL_SHIELD * Math.max(0.4, settings.particles)))
    shards.geometry.setDrawRange(0, Math.floor(FULL_SHARDS * Math.max(0.4, settings.particles)))
    resizeTargets()
  }

  function resizeTargets() {
    const w = Math.max(1, Math.floor(width * pixelRatio.value))
    const h = Math.max(1, Math.floor(height * pixelRatio.value))
    sceneTarget.setSize(w, h)
    half.forEach((target) => {
      target.setSize(Math.max(1, w >> 1), Math.max(1, h >> 1))
    })
    quarter.forEach((target) => {
      target.setSize(Math.max(1, w >> 2), Math.max(1, h >> 2))
    })
  }

  function setSize(w: number, h: number) {
    width = Math.max(1, w)
    height = Math.max(1, h)
    renderer.setSize(width, height, false)
    camera.aspect = width / height
    camera.updateProjectionMatrix()
    resizeTargets()
  }

  function resize() {
    setSize(host.clientWidth, host.clientHeight)
  }

  const lookTarget = new Vector3()
  function update(t: number, seconds: number, override?: CameraPose) {
    time.value = seconds
    const beats = beatsAt(t)
    redact.value = beats.redact
    shieldProgress.value = beats.shield
    search.value = beats.search
    crack.value = beats.crack
    dissolve.value = beats.dissolve
    shafts.forEach((shaft) => {
      const intensity = shaft.uniforms['uIntensity']
      if (intensity !== undefined) {
        intensity.value = (0.1 + 0.07 * beats.finale) * heroFade.value
      }
    })

    // Sheets float.
    for (const item of sheets) {
      item.mesh.position.set(
        item.base[0],
        item.base[1] + Math.sin(seconds * 0.4 + item.phase) * 0.08,
        item.base[2],
      )
      item.mesh.rotation.set(
        item.rotation[0] + Math.sin(seconds * 0.3 + item.phase) * 0.02,
        item.rotation[1],
        item.rotation[2] + Math.sin(seconds * 0.25 + item.phase) * 0.015,
      )
    }

    // Rings lock in sequence, then the seal stamps.
    rings.forEach(({ ring, material: ringMaterial }, k) => {
      const lock = beats.rings[k] ?? 0
      const free = 1 - lock
      ring.rotation.set(
        free * (1.0 + 0.35 * k) + Math.sin(seconds * (0.5 + 0.2 * k)) * 0.5 * free,
        free * (-0.7 + 0.55 * k) + seconds * (0.3 + 0.15 * k) * free,
        seconds * 0.05 * (k % 2 === 0 ? 1 : -1),
      )
      const uColour = ringMaterial.uniforms['uColour']
      const uOpacity = ringMaterial.uniforms['uOpacity']
      if (uColour !== undefined && uOpacity !== undefined) {
        ;(uColour.value as Color).copy(dim).lerp(verified, lock)
        uOpacity.value = 0.35 + 0.6 * lock
      }
    })
    const stamp = 1 - (1 - ramp(beats.seal, 0, 0.7)) ** 3
    seal.scale.setScalar(2.6 - 1.6 * stamp)
    seal.position.z = 0.35 + 1.5 * (1 - stamp)
    const sealOpacity = ramp(beats.seal, 0, 0.35)
    const setOpacity = (target: ShaderMaterial, value: number) => {
      const uniform = target.uniforms['uOpacity']
      if (uniform !== undefined) {
        uniform.value = value
      }
    }
    setOpacity(sealMaterial, sealOpacity)
    setOpacity(markMaterial, sealOpacity * 0.92)
    sealGlow.material.opacity = sealOpacity * (0.55 + 0.15 * Math.sin(seconds * 1.6))
    sealGlow.scale.setScalar(3.2)
    const landed = ramp(beats.seal, 0.6, 1)
    const phase = beats.seal >= 1 ? (seconds * 0.35) % 1 : landed
    shock.scale.setScalar(0.7 + phase * 3.6)
    setOpacity(shockMaterial, beats.seal > 0.6 ? (1 - phase) ** 2 * 0.85 : 0)

    heroFade.value = 1 - ramp(t, 0.62, 0.7) + ramp(t, 0.8, 0.9)
    hero.visible = heroFade.value > 0.01
    // The lattice fades in for the search, steps back behind the later stages, returns at the end.
    latticeFade.value = ramp(t, 0.2, 0.36) * (1 - 0.8 * ramp(t, 0.47, 0.56)) + 0.4 * beats.finale
    // Only stages near the camera are drawn.
    lattice.visible = t > 0.18
    verify.visible = t > 0.3
    invented.visible = t > 0.45

    // Camera along the path, with a small pointer parallax.
    const pose = override ?? cameraAt(t)
    camera.position.set(
      pose.position[0] + pointer.dx * 0.35,
      pose.position[1] + pointer.dy * 0.22,
      pose.position[2],
    )
    lookTarget.set(...pose.target)
    camera.lookAt(lookTarget)
  }

  /** The development frame-time overlay (`?perf=1`): real numbers from this GPU, twice a second. */
  function createPerfOverlay() {
    const panel = document.createElement('pre')
    panel.setAttribute('aria-hidden', 'true')
    panel.dataset['perf'] = 'overlay'
    panel.style.cssText =
      'position:fixed;left:8px;bottom:8px;z-index:60;margin:0;padding:8px 10px;border-radius:8px;' +
      'background:rgba(0,0,0,.72);color:#9ff5d0;font:12px/1.4 ui-monospace,monospace;' +
      'pointer-events:none;white-space:pre'
    document.body.appendChild(panel)
    const samples: number[] = []
    let lastShown = 0
    return {
      sample(ms: number) {
        if (ms > 0 && ms < 1000) {
          samples.push(ms)
          if (samples.length > 120) {
            samples.shift()
          }
        }
        const now = performance.now()
        if (now - lastShown < 500) {
          return
        }
        lastShown = now
        const stats = frameStats(samples)
        const pinned = flags.quality === null ? '' : ' (pinned)'
        panel.textContent = [
          `fps ${stats.fps.toFixed(1)}`,
          `avg ${stats.averageMs.toFixed(1)} ms  p95 ${stats.p95Ms.toFixed(1)} ms`,
          `quality ${String(quality.level)}${pinned}  bloom ${settings.bloom && flags.bloom ? 'on' : 'off'}`,
          `dpr ${pixelRatio.value.toFixed(2)}  ${String(width)}x${String(height)}`,
          `calls ${String(renderer.info.render.calls)}  tris ${String(renderer.info.render.triangles)}`,
        ].join('\n')
      },
      dispose() {
        panel.remove()
      },
    }
  }

  // ---- Loop ----
  let frame = 0
  let running = false
  let last = performance.now()
  let clock = 0

  function tick(now: number) {
    frame = requestAnimationFrame(tick)
    const deltaMs = now - last
    last = now
    const seconds = Math.min(deltaMs / 1000, 0.05)
    clock += seconds
    const previousLevel = quality.level
    if (flags.quality === null) {
      quality = nextQuality(quality, deltaMs)
    }
    perf?.sample(deltaMs)
    if (quality.level !== previousLevel) {
      settings = qualitySettings(quality.level)
      applyQuality()
      host.dataset['quality'] = String(quality.level)
    }
    currentT = damp(currentT, targetT, 3.2, seconds)
    // For the frame tests: the camera's timeline value, written only when it moved noticeably.
    if (Math.abs(currentT - reported) > 0.001) {
      reported = currentT
      host.dataset['progress'] = currentT.toFixed(3)
    }
    pointer.dx = damp(pointer.dx, pointer.x, 2.5, seconds)
    pointer.dy = damp(pointer.dy, pointer.y, 2.5, seconds)
    update(currentT, clock)
    renderer.info.reset()
    draw(settings.bloom && flags.bloom)
  }

  function setRunning(next: boolean) {
    if (next === running) {
      return
    }
    running = next
    if (running) {
      last = performance.now()
      frame = requestAnimationFrame(tick)
    } else {
      cancelAnimationFrame(frame)
    }
  }

  const observer = new ResizeObserver(resize)
  observer.observe(host)
  const onLost = (event: Event) => {
    event.preventDefault()
    setRunning(false)
    options.onContextLost?.()
  }
  canvas.addEventListener('webglcontextlost', onLost)

  applyQuality()
  resize()
  host.dataset['quality'] = String(quality.level)
  // Development switches: one layer at a time can be left out on a real GPU.
  sheetMeshes.forEach((mesh) => {
    mesh.visible = flags.sheets
  })
  coralCard.children.forEach((child) => {
    if (child !== shards) {
      child.visible = flags.sheets
    }
  })
  shaftMeshes.forEach((mesh) => {
    mesh.visible = flags.shafts
  })
  for (const points of [dust, shield, pulses, shards]) {
    points.visible = flags.particles
  }
  renderer.info.autoReset = false
  const perf = flags.perf ? createPerfOverlay() : null

  update(0, 0)
  draw(settings.bloom && flags.bloom)
  if (options.autoStart ?? true) {
    setRunning(true)
  }

  return {
    setTarget(t) {
      targetT = Math.min(1, Math.max(0, t))
    },
    setPointer(x, y) {
      pointer.x = x
      pointer.y = y
    },
    setRunning,
    renderStill(t, stillWidth, stillHeight, seconds = 6, pose) {
      setRunning(false)
      pixelRatio.value = 1
      renderer.setPixelRatio(1)
      setSize(stillWidth, stillHeight)
      currentT = t
      targetT = t
      update(t, seconds, pose)
      draw(true)
      return canvas
    },
    quality: () => quality.level,
    dispose() {
      setRunning(false)
      perf?.dispose()
      observer.disconnect()
      canvas.removeEventListener('webglcontextlost', onLost)
      disposables.forEach((item) => {
        item.dispose()
      })
      renderer.dispose()
      canvas.remove()
    },
  }
}
