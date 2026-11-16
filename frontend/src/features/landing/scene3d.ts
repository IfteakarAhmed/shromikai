import {
  AmbientLight,
  BoxGeometry,
  Color,
  CylinderGeometry,
  DirectionalLight,
  Group,
  MathUtils,
  Mesh,
  MeshBasicMaterial,
  MeshPhysicalMaterial,
  PerspectiveCamera,
  PointLight,
  Scene,
  WebGLRenderer,
  type BufferGeometry,
  type Material,
} from 'three'

/** Colours of the scene, read from the design tokens so it follows the light and dark themes. */
export interface Palette {
  paper: string
  verified: string
  uncertain: string
  rejected: string
  ink: string
}

type Vec3 = readonly [number, number, number]

export interface SceneHandle {
  dispose: () => void
}

/**
 * A small procedural scene: a contract sheet, three clause cards (two verified, one uncertain), an
 * emerald seal and a broken red citation. It is plain three.js with named imports, so the bundler
 * keeps only what this uses (ADR-0023: the scene is a lazy chunk under 200 KB gzip). It draws only
 * while the canvas is on screen and the tab is visible, and it is decoration: the poster behind it
 * carries the meaning.
 */
export function createContractScene(host: HTMLElement, palette: Palette): SceneHandle {
  const renderer = new WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.35))
  renderer.setClearColor(new Color(palette.ink), 0)
  const canvas = renderer.domElement
  canvas.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;touch-action:pan-y'
  canvas.setAttribute('aria-hidden', 'true')
  host.appendChild(canvas)

  const scene = new Scene()
  const camera = new PerspectiveCamera(40, 1, 0.1, 50)
  camera.position.set(0, 0, 8.6)

  const disposables: { dispose: () => void }[] = []
  const track = <T extends { dispose: () => void }>(item: T): T => {
    disposables.push(item)
    return item
  }

  scene.add(new AmbientLight(0xffffff, 1.6))
  const key = new DirectionalLight(new Color(palette.paper), 2.5)
  key.position.set(-4, 5, 6)
  scene.add(key)
  const emerald = new PointLight(new Color(palette.verified), 8, 9)
  emerald.position.set(3, 1, 2)
  scene.add(emerald)
  const amber = new PointLight(new Color(palette.uncertain), 3, 7)
  amber.position.set(-3, -3, 1)
  scene.add(amber)

  function mesh(
    geometry: BufferGeometry,
    material: Material,
    position: Vec3,
    rotation: Vec3 = [0, 0, 0],
  ): Mesh {
    const result = new Mesh(track(geometry), track(material))
    result.position.set(...position)
    result.rotation.set(...rotation)
    return result
  }
  const basic = (color: string, opacity = 1) =>
    new MeshBasicMaterial({ color: new Color(color), transparent: opacity < 1, opacity })
  const glass = (color: string, opacity: number, roughness: number) =>
    new MeshPhysicalMaterial({
      color: new Color(color),
      transparent: true,
      opacity,
      roughness,
      metalness: 0.08,
      clearcoat: 0.8,
    })

  const rig = new Group()
  scene.add(rig)

  // The sheet, its ruled lines and the scanning beam.
  const sheet = new Group()
  sheet.rotation.set(-0.13, -0.12, -0.025)
  rig.add(sheet)
  const paper = new MeshPhysicalMaterial({
    color: new Color(palette.paper),
    transparent: true,
    opacity: 0.79,
    roughness: 0.38,
    metalness: 0.035,
    clearcoat: 0.4,
  })
  sheet.add(mesh(new BoxGeometry(3.35, 4.55, 0.08), paper, [0, 0, -0.2]))
  const rows = [1.56, 1.24, 0.91, 0.58, 0.25, -0.4, -0.73, -1.06, -1.39, -1.72]
  rows.forEach((y, index) => {
    sheet.add(
      mesh(new BoxGeometry(index % 4 === 0 ? 2.5 : 2.86, 0.027, 0.008), basic(palette.ink, 0.18), [
        index % 3 === 0 ? 0.15 : -0.02,
        y,
        -0.144,
      ]),
    )
  })
  const beam = mesh(
    new BoxGeometry(4, 0.095, 0.012),
    basic(palette.verified, 0.1),
    [0, 0, 0.025],
    [0, 0, -0.45],
  )
  sheet.add(beam)

  // Clause cards.
  const cards: { group: Group; speed: number; amount: number }[] = []
  function clauseCard(position: Vec3, tilt: number, color: string, speed: number, amount: number) {
    const holder = new Group()
    const card = new Group()
    card.position.set(...position)
    card.rotation.set(0.04, -0.07, tilt)
    card.add(mesh(new BoxGeometry(2.38, 0.7, 0.055), glass(color, 0.24, 0.18), [0, 0, 0]))
    card.add(mesh(new BoxGeometry(0.12, 0.12, 0.012), basic(color, 0.75), [-0.91, 0, 0.033]))
    card.add(mesh(new BoxGeometry(1.56, 0.035, 0.01), basic(color, 0.75), [0.11, 0.1, 0.035]))
    card.add(mesh(new BoxGeometry(1.39, 0.025, 0.01), basic(color, 0.46), [0.02, -0.07, 0.035]))
    holder.add(card)
    rig.add(holder)
    cards.push({ group: holder, speed, amount })
  }
  clauseCard([-1.75, 0.98, 0.54], 0.085, palette.verified, 0.42, 0.08)
  clauseCard([-1.56, -0.05, 0.76], -0.035, palette.verified, 0.32, 0.06)
  clauseCard([-1.25, -1.03, 0.54], 0.055, palette.uncertain, 0.37, 0.06)

  // The seal with a check mark.
  const seal = new Group()
  seal.position.set(-0.15, 0.08, 1.03)
  seal.add(
    mesh(
      new CylinderGeometry(0.36, 0.4, 0.1, 10),
      new MeshPhysicalMaterial({
        color: new Color(palette.verified),
        emissive: new Color(palette.verified),
        emissiveIntensity: 0.24,
        roughness: 0.22,
        metalness: 0.44,
        clearcoat: 0.8,
      }),
      [0, 0, 0],
    ),
  )
  const tick = new Group()
  tick.position.set(0, 0.19, 0)
  tick.rotation.set(-0.1, 0, 0)
  tick.add(
    mesh(
      new BoxGeometry(0.075, 0.24, 0.045),
      basic(palette.verified),
      [-0.07, 0.005, 0.015],
      [0, 0, -0.7],
    ),
  )
  tick.add(
    mesh(
      new BoxGeometry(0.075, 0.37, 0.045),
      basic(palette.verified),
      [0.075, -0.025, 0.015],
      [0, 0, 0.58],
    ),
  )
  seal.add(tick)
  rig.add(seal)

  // The broken citation: a cracked red pane with a cross.
  const broken = new Group()
  broken.position.set(2.12, 1.18, 0.36)
  broken.rotation.set(0, -0.06, 0.09)
  broken.add(
    mesh(new BoxGeometry(1.22, 0.63, 0.034), glass(palette.rejected, 0.17, 0.22), [0, 0.1, -0.025]),
  )
  broken.add(
    mesh(
      new BoxGeometry(0.045, 0.31, 0.016),
      basic(palette.rejected),
      [-0.09, 0.11, 0.008],
      [0, 0, -0.83],
    ),
  )
  broken.add(
    mesh(
      new BoxGeometry(0.045, 0.39, 0.016),
      basic(palette.rejected),
      [0.05, -0.08, 0.008],
      [0, 0, 0.7],
    ),
  )
  broken.add(
    mesh(
      new BoxGeometry(0.2, 0.16, 0.035),
      glass(palette.rejected, 0.2, 0.2),
      [-0.7, 0.55, 0.01],
      [0, 0, 0.22],
    ),
  )
  broken.add(
    mesh(
      new BoxGeometry(0.17, 0.2, 0.035),
      glass(palette.rejected, 0.2, 0.2),
      [0.73, -0.28, 0.01],
      [0, 0, -0.28],
    ),
  )
  rig.add(broken)

  // Sizing, pointer and the loop.
  const pointer = { x: 0, y: 0 }
  function resize() {
    const { clientWidth, clientHeight } = host
    if (clientWidth === 0 || clientHeight === 0) {
      return
    }
    renderer.setSize(clientWidth, clientHeight, false)
    camera.aspect = clientWidth / clientHeight
    camera.updateProjectionMatrix()
  }
  function onPointer(event: PointerEvent) {
    const box = host.getBoundingClientRect()
    pointer.x = ((event.clientX - box.left) / box.width) * 2 - 1
    pointer.y = -(((event.clientY - box.top) / box.height) * 2 - 1)
  }
  const observer = new ResizeObserver(resize)
  observer.observe(host)
  window.addEventListener('pointermove', onPointer, { passive: true })
  resize()

  let visible = true
  const intersection = new IntersectionObserver((entries) => {
    visible = entries.some((entry) => entry.isIntersecting)
  })
  intersection.observe(host)

  // One frame at once, so the scene is on screen before the first animation frame arrives.
  // One frame at once, so the scene is on screen before the first animation frame arrives.
  renderer.render(scene, camera)
  let frame = 0
  let last = performance.now()
  let elapsed = 0
  function render(now: number) {
    frame = requestAnimationFrame(render)
    const delta = Math.min((now - last) / 1000, 0.05)
    last = now
    if (!visible || document.visibilityState !== 'visible') {
      return
    }
    elapsed += delta
    const t = elapsed
    rig.rotation.y = MathUtils.damp(
      rig.rotation.y,
      pointer.x * 0.12 + Math.sin(t * 0.22) * 0.012,
      3,
      delta,
    )
    rig.rotation.x = MathUtils.damp(
      rig.rotation.x,
      -pointer.y * 0.07 + Math.sin(t * 0.31) * 0.009,
      3,
      delta,
    )
    rig.position.y = Math.sin(t * 0.45) * 0.045
    beam.position.y = Math.sin(t * 0.38) * 1.7
    if (beam.material instanceof MeshBasicMaterial) {
      beam.material.opacity = 0.08 + (Math.sin(t * 0.7) + 1) * 0.035
    }
    broken.position.x = 2.12 + Math.sin(t * 0.16) * 0.14
    broken.position.y = 1.18 + Math.sin(t * 0.28) * 0.07
    broken.rotation.z = Math.sin(t * 0.35) * 0.025
    const stamp = Math.max(0, 1 - Math.abs(Math.sin(t * 0.22)) * 3.5)
    seal.scale.setScalar(0.88 + stamp * 0.16)
    seal.position.z = 0.97 + stamp * 0.16
    for (const { group, speed, amount } of cards) {
      group.position.y = Math.sin(t * speed) * amount
      group.rotation.z = Math.sin(t * speed * 0.7) * amount * 0.12
    }
    renderer.render(scene, camera)
  }
  frame = requestAnimationFrame(render)

  return {
    dispose() {
      cancelAnimationFrame(frame)
      observer.disconnect()
      intersection.disconnect()
      window.removeEventListener('pointermove', onPointer)
      disposables.forEach((item) => {
        item.dispose()
      })
      renderer.dispose()
      canvas.remove()
    },
  }
}
