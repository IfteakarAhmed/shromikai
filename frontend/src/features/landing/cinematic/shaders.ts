/**
 * GLSL for the cinematic landing scene. Written by hand for this project; no shader is copied from
 * elsewhere. The noise and cellular functions are the usual textbook constructions (hash, value
 * noise, fbm, a 3×3 Voronoi search). Every fragment shader ends with three's colour-space chunk so
 * the same material is right on screen and inside the bloom render target.
 */

/**
 * Guards shared by every shader. A real GPU (ANGLE on Metal, for one) returns NaN for pow() of a
 * negative base or normalize() of a zero vector; the half-float bloom then spreads one NaN pixel
 * into large black shapes (the D1 review bug, traced to the light shafts). The shader lint test
 * rejects unguarded pow, sqrt, normalize, reversed smoothstep and division by a variable.
 */
const SAFE = /* glsl */ `
vec3 safeNormalize(vec3 v) {
  return v * (1.0 / max(length(v), 1e-6));
}
`

const NOISE = /* glsl */ `
float hash12(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}
vec2 hash22(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.xx + p3.yz) * p3.zy);
}
float vnoise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
             mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * vnoise(p);
    p *= 2.03;
    a *= 0.5;
  }
  return v / 0.9375;
}
// Normalised fbm used by the dissolve, the same in the card and in its particles.
float dissolveNoise(vec2 uv) {
  return clamp((fbm(uv * 3.2 + 7.0) - 0.22) / 0.56, 0.0, 1.0);
}
vec2 voronoi(vec2 p) {
  vec2 n = floor(p);
  vec2 f = fract(p);
  float f1 = 8.0;
  float f2 = 8.0;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      vec2 g = vec2(float(i), float(j));
      vec2 r = g + hash22(n + g) - f;
      float d = dot(r, r);
      if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) { f2 = d; }
    }
  }
  return vec2(sqrt(max(f1, 0.0)), sqrt(max(f2, 0.0)));
}
`

/** Glass sheet: fresnel rim, ruled lines from a canvas texture, redaction bars by progress. */
export const sheetVertex = /* glsl */ `
${SAFE}
uniform float uTime;
uniform float uSeed;
uniform float uBend;
varying vec2 vUv;
varying vec3 vNormal;
varying vec3 vView;
varying float vDepth;
void main() {
  vUv = uv;
  vec3 p = position;
  p.z += sin(uv.y * 3.14159) * uBend + sin(uv.x * 2.0 + uTime * 0.6 + uSeed) * 0.02;
  vec4 world = modelMatrix * vec4(p, 1.0);
  vNormal = safeNormalize(mat3(modelMatrix) * normal);
  vView = safeNormalize(cameraPosition - world.xyz);
  vec4 mv = viewMatrix * world;
  vDepth = -mv.z;
  gl_Position = projectionMatrix * mv;
}
`

export const sheetFragment = /* glsl */ `
${SAFE}
uniform sampler2D uMap;
uniform vec3 uGlass;
uniform vec3 uStroke;
uniform vec3 uInk;
uniform vec3 uRim;
uniform vec3 uSweep;
uniform float uOpacity;
uniform float uRedact;
uniform float uFade;
varying vec2 vUv;
varying vec3 vNormal;
varying vec3 vView;
varying float vDepth;
void main() {
  vec4 m = texture2D(uMap, vUv);
  float facing = clamp(abs(dot(safeNormalize(vNormal), safeNormalize(vView))), 0.0, 1.0);
  float fres = pow(clamp(1.0 - facing, 0.0, 1.0), 2.2);
  // Each redacted span closes at its own moment (blue channel), left to right inside the row.
  float local = smoothstep(m.b * 0.6, m.b * 0.6 + 0.4, uRedact);
  float bar = m.g * local;
  float stroke = m.r * (1.0 - bar);
  vec3 colour = uGlass;
  float alpha = uOpacity;
  colour = mix(colour, uStroke, stroke);
  alpha = mix(alpha, 0.66, stroke * 0.85);
  colour = mix(colour, uInk, bar);
  alpha = mix(alpha, 0.98, bar);
  float edge = m.g * local * (1.0 - local) * 4.0;
  colour = mix(colour, uSweep, clamp(edge * 1.4, 0.0, 1.0));
  alpha = max(alpha, edge * 0.85);
  float d = min(min(vUv.x, 1.0 - vUv.x), min(vUv.y, 1.0 - vUv.y));
  float border = 1.0 - smoothstep(0.0, 0.008, d);
  colour = mix(colour, uRim, clamp(fres * 0.7 + border * 0.7, 0.0, 1.0));
  colour = mix(colour, uRim, 0.08 * smoothstep(0.35, 1.2, vUv.y + vUv.x * 0.4));
  alpha += 0.06 * smoothstep(0.7, 1.4, vUv.y + vUv.x * 0.4);
  alpha = clamp(alpha + fres * 0.3 + border * 0.55, 0.0, 1.0);
  alpha *= uFade * clamp(1.4 - vDepth / 40.0, 0.25, 1.0);
  gl_FragColor = vec4(colour, alpha);
  #include <colorspace_fragment>
}
`

/** The coral card: cracks from a Voronoi pattern, then a noise-threshold dissolve with a hot edge. */
export const crackFragment = /* glsl */ `
${SAFE}
${NOISE}
uniform sampler2D uMap;
uniform vec3 uCoral;
uniform vec3 uGlass;
uniform float uCrack;
uniform float uDissolve;
uniform float uFade;
varying vec2 vUv;
varying vec3 vNormal;
varying vec3 vView;
varying float vDepth;
void main() {
  float n = dissolveNoise(vUv);
  float threshold = uDissolve * 1.1 - 0.05;
  if (n < threshold) discard;
  vec4 m = texture2D(uMap, vUv);
  float facing = clamp(abs(dot(safeNormalize(vNormal), safeNormalize(vView))), 0.0, 1.0);
  float fres = pow(clamp(1.0 - facing, 0.0, 1.0), 2.2);
  vec2 cell = voronoi(vUv * vec2(4.0, 5.4) + 3.0);
  float crackLine = 1.0 - smoothstep(0.0, 0.05, cell.y - cell.x);
  float spread = smoothstep(n * 0.85, n * 0.85 + 0.2, uCrack);
  float crack = crackLine * spread;
  float burn = 1.0 - smoothstep(0.0, 0.07, n - threshold);
  burn *= step(0.001, uDissolve);
  vec3 colour = mix(uGlass, uCoral, 0.55);
  float alpha = 0.3;
  colour = mix(colour, uCoral, m.r * 0.8);
  alpha = mix(alpha, 0.85, m.r * 0.7);
  float d = min(min(vUv.x, 1.0 - vUv.x), min(vUv.y, 1.0 - vUv.y));
  float border = 1.0 - smoothstep(0.0, 0.01, d);
  colour = mix(colour, uCoral, clamp(fres * 0.6 + border * 0.7 + crack * 1.2 + burn * 1.5, 0.0, 1.0));
  alpha = clamp(alpha + fres * 0.3 + border * 0.5 + crack * 0.6 + burn, 0.0, 1.0);
  gl_FragColor = vec4(colour, alpha * uFade);
  #include <colorspace_fragment>
}
`

/** Soft round particle, used by every point cloud (additive blending). */
const pointFragment = /* glsl */ `
varying float vAlpha;
varying vec3 vColour;
void main() {
  vec2 c = gl_PointCoord - 0.5;
  float a = 1.0 - smoothstep(0.0, 0.5, length(c));
  a *= a;
  gl_FragColor = vec4(vColour, a * vAlpha);
  #include <colorspace_fragment>
}
`

/** Dust: size attenuation, a slow drift and a twinkle. */
export const dustVertex = /* glsl */ `
attribute float aScale;
attribute float aPhase;
attribute float aSpeed;
uniform float uTime;
uniform float uSize;
uniform float uPixelRatio;
uniform float uFade;
uniform vec3 uColour;
varying float vAlpha;
varying vec3 vColour;
void main() {
  vec3 p = position;
  p.y += sin(uTime * 0.15 * aSpeed + aPhase) * 0.25;
  p.x += cos(uTime * 0.11 * aSpeed + aPhase * 1.3) * 0.2;
  vec4 mv = modelViewMatrix * vec4(p, 1.0);
  gl_Position = projectionMatrix * mv;
  gl_PointSize = min(48.0, uSize * aScale * uPixelRatio * (10.0 / max(-mv.z, 0.1)));
  float twinkle = 0.5 + 0.5 * sin(uTime * aSpeed * 1.7 + aPhase * 6.2831);
  vAlpha = (0.2 + 0.8 * twinkle * twinkle * twinkle) * uFade * smoothstep(0.6, 2.5, -mv.z);
  vColour = uColour;
}
`
export const dustFragment = pointFragment

/** The shield: particles fly from a cloud to a ring as `uShield` grows. */
export const shieldVertex = /* glsl */ `
attribute vec3 aStart;
attribute float aDelay;
uniform float uTime;
uniform float uShield;
uniform float uSize;
uniform float uPixelRatio;
uniform vec3 uColour;
uniform float uFade;
varying float vAlpha;
varying vec3 vColour;
void main() {
  float e = clamp(uShield * 1.6 - aDelay * 0.6, 0.0, 1.0);
  e = 1.0 - pow(max(1.0 - e, 0.0), 3.0);
  float angle = uTime * 0.12 * e + aDelay * 0.4 * (1.0 - e);
  mat2 r = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
  vec3 ring = position;
  ring.xy = r * ring.xy;
  vec3 p = mix(aStart, ring, e);
  vec4 mv = modelViewMatrix * vec4(p, 1.0);
  gl_Position = projectionMatrix * mv;
  gl_PointSize = min(40.0, uSize * uPixelRatio * (10.0 / max(-mv.z, 0.1)) * (0.6 + 0.6 * e));
  vAlpha = step(0.0001, uShield) * (0.15 + 0.85 * e) * (0.75 + 0.25 * sin(uTime * 2.0 + aDelay * 40.0));
  vAlpha *= uFade;
  vColour = uColour;
}
`
export const shieldFragment = pointFragment

/** Light pulses travelling along the lattice edges towards the matched sections. */
export const pulseVertex = /* glsl */ `
attribute vec3 aStart;
attribute float aOffset;
attribute float aHot;
uniform float uTime;
uniform float uSearch;
uniform float uSize;
uniform float uPixelRatio;
uniform vec3 uHot;
uniform vec3 uCold;
varying float vAlpha;
varying vec3 vColour;
void main() {
  float f = fract(uTime * (0.35 + 0.25 * aHot) + aOffset);
  vec3 p = mix(aStart, position, f);
  vec4 mv = modelViewMatrix * vec4(p, 1.0);
  gl_Position = projectionMatrix * mv;
  gl_PointSize = min(40.0, uSize * uPixelRatio * (10.0 / max(-mv.z, 0.1)) * (0.7 + 0.8 * aHot));
  vAlpha = uSearch * (0.3 + 0.7 * aHot) * sin(3.14159 * f);
  vColour = mix(uCold, uHot, aHot);
}
`
export const pulseFragment = pointFragment

/** The coral card's particles: released where the dissolve has passed, drifting outwards. */
export const shardVertex = /* glsl */ `
${NOISE}
attribute vec2 aUv;
attribute vec3 aDirection;
attribute float aPhase;
uniform float uTime;
uniform float uDissolve;
uniform float uSize;
uniform float uPixelRatio;
uniform vec3 uColour;
uniform vec3 uHotColour;
varying float vAlpha;
varying vec3 vColour;
void main() {
  float n = dissolveNoise(aUv);
  float threshold = uDissolve * 1.1 - 0.05;
  float release = clamp((threshold - n) * 3.0, 0.0, 1.0);
  vec3 p = position + aDirection * release * 2.4 + vec3(0.0, release * release * 0.9, 0.0);
  p += vec3(sin(uTime * 0.7 + aPhase), cos(uTime * 0.5 + aPhase * 1.7), 0.0) * 0.08 * release;
  vec4 mv = modelViewMatrix * vec4(p, 1.0);
  gl_Position = projectionMatrix * mv;
  gl_PointSize = min(36.0, uSize * uPixelRatio * (10.0 / max(-mv.z, 0.1)) * (1.2 - 0.6 * release));
  vAlpha = step(0.0001, release) * (1.0 - 0.7 * release);
  vColour = mix(uHotColour, uColour, release);
}
`
export const shardFragment = pointFragment

/** The cards of the law lattice (instanced), with abstract ruled lines drawn in the shader. */
export const latticeVertex = /* glsl */ `
attribute float aMatch;
attribute float aAmber;
attribute float aSeed;
varying vec2 vUv;
varying float vMatch;
varying float vAmber;
varying float vSeed;
varying float vDepth;
void main() {
  vUv = uv;
  vMatch = aMatch;
  vAmber = aAmber;
  vSeed = aSeed;
  vec4 world = modelMatrix * instanceMatrix * vec4(position, 1.0);
  vec4 mv = viewMatrix * world;
  vDepth = -mv.z;
  gl_Position = projectionMatrix * mv;
}
`
export const latticeFragment = /* glsl */ `
uniform vec3 uGlass;
uniform vec3 uStroke;
uniform vec3 uVerified;
uniform vec3 uAmber;
uniform float uSearch;
uniform float uFade;
uniform float uOpacityScale;
varying vec2 vUv;
varying float vMatch;
varying float vAmber;
varying float vSeed;
varying float vDepth;
void main() {
  float d = min(min(vUv.x, 1.0 - vUv.x), min(vUv.y, 1.0 - vUv.y));
  float border = 1.0 - smoothstep(0.0, 0.035, d);
  float rows = vUv.y * 6.0;
  float row = floor(rows);
  float band = smoothstep(0.38, 0.44, fract(rows)) * (1.0 - smoothstep(0.56, 0.62, fract(rows)));
  float span = 0.4 + 0.45 * fract(sin((row + vSeed * 13.0) * 12.9898) * 43758.5453);
  float line = band * step(0.12, vUv.x) * step(vUv.x, 0.12 + span * 0.76);
  line *= step(0.5, row) * step(row, 4.5);
  float match = vMatch * smoothstep(0.55, 1.0, uSearch);
  float amber = vAmber * smoothstep(0.4, 0.9, uSearch);
  vec3 colour = uGlass;
  float alpha = 0.035 + line * 0.3 + border * 0.4;
  colour = mix(colour, uStroke, line);
  colour = mix(colour, uVerified, match * 0.8);
  colour = mix(colour, uAmber, amber * 0.6);
  colour = mix(colour, uVerified, clamp(match * (border * 2.2 + 0.35), 0.0, 1.0) * uFade);
  colour = mix(colour, uAmber, clamp(amber * border * 1.2, 0.0, 1.0) * uFade);
  alpha += match * 0.35 + amber * 0.2;
  alpha *= uFade * uOpacityScale * clamp(1.5 - vDepth / 30.0, 0.2, 1.0);
  gl_FragColor = vec4(colour, clamp(alpha, 0.0, 1.0));
  #include <colorspace_fragment>
}
`

/** Lattice edges: faint lines, brighter on the matched paths, with a moving highlight. */
export const edgeVertex = /* glsl */ `
attribute float aT;
attribute float aHot;
attribute float aOffset;
varying float vT;
varying float vHot;
varying float vOffset;
varying float vDepth;
void main() {
  vT = aT;
  vHot = aHot;
  vOffset = aOffset;
  vec4 mv = modelViewMatrix * vec4(position, 1.0);
  vDepth = -mv.z;
  gl_Position = projectionMatrix * mv;
}
`
export const edgeFragment = /* glsl */ `
uniform vec3 uStroke;
uniform vec3 uVerified;
uniform float uTime;
uniform float uSearch;
uniform float uFade;
uniform float uOpacityScale;
varying float vT;
varying float vHot;
varying float vOffset;
varying float vDepth;
void main() {
  float x = fract(uTime * 0.45 + vOffset) - vT;
  float pulse = exp(-x * x * 49.0);
  vec3 colour = mix(uStroke, uVerified, vHot * uSearch);
  float alpha = 0.07 + uSearch * (0.06 + 0.3 * vHot) + pulse * uSearch * (0.2 + 0.7 * vHot);
  alpha *= uFade * uOpacityScale * clamp(1.5 - vDepth / 30.0, 0.2, 1.0);
  gl_FragColor = vec4(colour, clamp(alpha, 0.0, 1.0));
  #include <colorspace_fragment>
}
`

/** A soft additive shaft of light. */
export const shaftVertex = /* glsl */ `
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`
export const shaftFragment = /* glsl */ `
${NOISE}
uniform vec3 uColour;
uniform float uTime;
uniform float uSeed;
uniform float uIntensity;
varying vec2 vUv;
void main() {
  float x = clamp(abs(vUv.x - 0.5) * 2.0, 0.0, 1.0);
  float core = pow(max(1.0 - x, 0.0), 2.5);
  float fall = smoothstep(0.0, 0.7, vUv.y) * (1.0 - smoothstep(0.85, 1.0, vUv.y));
  float flicker = 0.7 + 0.3 * vnoise(vec2(vUv.y * 3.0 - uTime * 0.08, uSeed));
  gl_FragColor = vec4(uColour, clamp(core * fall * flicker * uIntensity, 0.0, 1.0));
  #include <colorspace_fragment>
}
`

/** A plain colour with opacity (rings, seal, shockwave); additive or normal by material. */
export const flatVertex = shaftVertex
export const flatFragment = /* glsl */ `
uniform vec3 uColour;
uniform float uOpacity;
void main() {
  gl_FragColor = vec4(uColour, uOpacity);
  #include <colorspace_fragment>
}
`

/** The sky behind the scene: night or dawn gradient with a soft glow near the top. */
export const skyVertex = /* glsl */ `
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = vec4(position.xy, 0.9999, 1.0);
}
`
export const skyFragment = /* glsl */ `
uniform vec3 uTop;
uniform vec3 uBottom;
uniform vec3 uGlow;
uniform float uGlowAmount;
varying vec2 vUv;
void main() {
  vec3 colour = mix(uBottom, uTop, smoothstep(0.0, 1.0, vUv.y));
  float glow = 1.0 - smoothstep(0.0, 0.85, length((vUv - vec2(0.78, 0.95)) * vec2(1.4, 1.0)));
  colour = mix(colour, uGlow, clamp(glow * uGlowAmount, 0.0, 1.0));
  gl_FragColor = vec4(colour, 1.0);
  #include <colorspace_fragment>
}
`

/** Full-screen passes of the bloom. */
export const screenVertex = /* glsl */ `
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = vec4(position.xy, 0.0, 1.0);
}
`
export const brightFragment = /* glsl */ `
uniform sampler2D uScene;
uniform float uThreshold;
varying vec2 vUv;
void main() {
  vec3 c = clamp(texture2D(uScene, vUv).rgb, 0.0, 16.0);
  float l = max(c.r, max(c.g, c.b));
  gl_FragColor = vec4(c * smoothstep(uThreshold, uThreshold + 0.3, l), 1.0);
}
`
export const blurFragment = /* glsl */ `
uniform sampler2D uInput;
uniform vec2 uDirection;
varying vec2 vUv;
void main() {
  vec3 sum = texture2D(uInput, vUv).rgb * 0.2270270270;
  sum += texture2D(uInput, vUv + uDirection * 1.3846153846).rgb * 0.3162162162;
  sum += texture2D(uInput, vUv - uDirection * 1.3846153846).rgb * 0.3162162162;
  sum += texture2D(uInput, vUv + uDirection * 3.2307692308).rgb * 0.0702702703;
  sum += texture2D(uInput, vUv - uDirection * 3.2307692308).rgb * 0.0702702703;
  gl_FragColor = vec4(clamp(sum, 0.0, 16.0), 1.0);
}
`
export const compositeFragment = /* glsl */ `
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform sampler2D uBloomWide;
uniform float uStrength;
uniform float uVignetteFloor;
varying vec2 vUv;
void main() {
  vec3 c = clamp(texture2D(uScene, vUv).rgb, 0.0, 16.0);
  vec3 bloom = texture2D(uBloom, vUv).rgb * 0.7 + texture2D(uBloomWide, vUv).rgb * 0.9;
  c += clamp(bloom, 0.0, 16.0) * uStrength;
  float vignette = 1.0 - smoothstep(0.35, 1.25, length((vUv - 0.5) * vec2(1.35, 1.0)));
  c *= mix(uVignetteFloor, 1.0, vignette);
  gl_FragColor = vec4(c, 1.0);
  #include <colorspace_fragment>
}
`
