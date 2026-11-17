import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { GlassCard } from '../../components/ui/GlassCard'
import { localizeDigits } from '../../lib/format'

/**
 * The two architecture pictures of `/docs/architecture` (FR-74), drawn by hand as SVG: no diagram
 * library and no generated image. C4 redraws them for both themes and for phones:
 *
 * - every colour is a theme token (`var(--…)`), a gradient of tokens or a filter of tokens, so dark
 *   and light read the same; plain boxes have the 3:1 field edge, not the faint card border;
 * - each picture has a wide drawing (from the lg breakpoint) and a tall one for narrower screens,
 *   so the text never shrinks below 12 px on a phone (`ArchitectureDiagrams.test.tsx`);
 * - local boxes glow green, cloud boxes amber, as the privacy page says: only redacted text leaves.
 *
 * Boxes and steps follow `docs/spec/architecture.md` §3 and §5.1.
 */

type Tone = 'plain' | 'local' | 'cloud'

interface BoxProps {
  x: number
  y: number
  w: number
  h: number
  title: string
  note?: string
  tone?: Tone
  gradient: string
}

/** Splits a title into lines of at most `max` characters, at spaces. */
function wrap(title: string, max: number): string[] {
  const lines: string[] = []
  let line = ''
  for (const word of title.split(' ')) {
    if (line !== '' && (line + ' ' + word).length > max) {
      lines.push(line)
      line = word
    } else {
      line = line === '' ? word : `${line} ${word}`
    }
  }
  return [...lines, line]
}

const STROKE: Record<Tone, string> = {
  plain: 'var(--input)',
  local: 'var(--verified)',
  cloud: 'var(--uncertain)',
}

const GLOW: Record<Tone, string | undefined> = {
  plain: undefined,
  local: 'drop-shadow(0 0 7px var(--glow-primary))',
  cloud: 'drop-shadow(0 0 7px var(--glow-uncertain))',
}

function Box({ x, y, w, h, title, note, tone = 'plain', gradient }: BoxProps) {
  const lines = wrap(title, Math.max(8, Math.floor(w / 9.5)))
  const noteLines = note === undefined ? [] : wrap(note, Math.max(8, Math.floor(w / 7.4)))
  const total = lines.length * 19 + noteLines.length * 16
  const top = y + (h - total) / 2 + 14
  const glow = GLOW[tone]
  return (
    <g style={glow === undefined ? undefined : { filter: glow }}>
      <rect
        x={x}
        y={y}
        width={w}
        height={h}
        rx={12}
        fill={`url(#${gradient})`}
        stroke={STROKE[tone]}
        strokeWidth={tone === 'plain' ? 1.5 : 2.25}
      />
      {lines.map((line, i) => (
        <text
          key={`t${String(i)}`}
          x={x + w / 2}
          y={top + i * 19}
          textAnchor="middle"
          fill="var(--foreground)"
          fontSize={15}
          fontWeight={600}
        >
          {line}
        </text>
      ))}
      {noteLines.map((line, i) => (
        <text
          key={`n${String(i)}`}
          x={x + w / 2}
          y={top + lines.length * 19 + i * 16}
          textAnchor="middle"
          fill="var(--muted-foreground)"
          fontSize={13}
        >
          {line}
        </text>
      ))}
    </g>
  )
}

function Arrow({
  d,
  label,
  labelX,
  labelY,
  labelAnchor = 'middle',
  tone = 'plain',
  marker,
}: {
  d: string
  label?: string
  labelX?: number
  labelY?: number
  labelAnchor?: 'start' | 'middle' | 'end'
  tone?: Tone
  marker: string
}) {
  const colour = tone === 'cloud' ? 'var(--uncertain)' : 'var(--muted-foreground)'
  return (
    <g>
      <path
        d={d}
        fill="none"
        stroke={colour}
        strokeWidth={1.75}
        strokeDasharray={tone === 'cloud' ? '7 4' : undefined}
        markerEnd={`url(#${tone === 'cloud' ? `${marker}-cloud` : marker})`}
      />
      {label !== undefined && labelX !== undefined && labelY !== undefined && (
        <text x={labelX} y={labelY} textAnchor={labelAnchor} fill={colour} fontSize={13}>
          {label}
        </text>
      )}
    </g>
  )
}

/** A straight arrow between two points. */
const line = (x1: number, y1: number, x2: number, y2: number) =>
  `M ${String(x1)} ${String(y1)} L ${String(x2)} ${String(y2)}`

interface DrawingProps {
  id: string
  variant: 'wide' | 'narrow'
  title: string
  description: string
  viewBox: string
  children: (ids: { gradient: string; marker: string }) => ReactNode
}

/** One drawing: its own ids for the gradient and the two arrow heads, so none repeats on a page. */
function Drawing({ id, variant, title, description, viewBox, children }: DrawingProps) {
  const full = `${id}-${variant}`
  const gradient = `${full}-box`
  const marker = `${full}-arrow`
  return (
    <svg
      data-variant={variant}
      role="img"
      aria-labelledby={`${full}-title`}
      aria-describedby={`${full}-desc`}
      viewBox={viewBox}
      className={
        variant === 'wide'
          ? 'hidden h-auto w-full lg:block'
          : 'mx-auto h-auto w-full max-w-md lg:hidden'
      }
      fontFamily="inherit"
    >
      <title id={`${full}-title`}>{title}</title>
      <desc id={`${full}-desc`}>{description}</desc>
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" style={{ stopColor: 'var(--surface-3)' }} />
          <stop offset="1" style={{ stopColor: 'var(--surface-1)' }} />
        </linearGradient>
        <marker
          id={marker}
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--muted-foreground)" />
        </marker>
        <marker
          id={`${marker}-cloud`}
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--uncertain)" />
        </marker>
      </defs>
      {children({ gradient, marker })}
    </svg>
  )
}

function Figure({ description, children }: { description: string; children: ReactNode }) {
  return (
    <GlassCard className="p-3">
      <figure>
        {children}
        <figcaption className="mt-2 px-1 text-sm text-muted-foreground">{description}</figcaption>
      </figure>
    </GlassCard>
  )
}

export function ContainersDiagram() {
  const { t } = useTranslation()
  const title = t('docs.architecture.containersTitle')
  const description = t('docs.architecture.containersDesc')
  const box = (key: string) => t(`docs.architecture.${key}`)
  return (
    <Figure description={description}>
      <Drawing
        id="containers"
        variant="wide"
        title={title}
        description={description}
        viewBox="0 0 880 410"
      >
        {({ gradient, marker }) => (
          <>
            <rect
              x={200}
              y={10}
              width={470}
              height={390}
              rx={16}
              fill="var(--verified)"
              fillOpacity={0.06}
              stroke="var(--verified)"
              strokeDasharray="6 5"
              strokeWidth={2}
            />
            <text x={216} y={34} fill="var(--verified)" fontSize={14} fontWeight={600}>
              {box('privateNetwork')}
            </text>
            <Box gradient={gradient} x={240} y={50} w={170} h={64} title={box('sources')} />
            <Box gradient={gradient} x={470} y={45} w={170} h={64} title={box('database')} />
            <Box
              gradient={gradient}
              x={470}
              y={125}
              w={170}
              h={80}
              title={box('local')}
              note={box('localNote')}
              tone="local"
            />
            <Box gradient={gradient} x={470} y={218} w={170} h={50} title={box('pdf')} />
            <Box gradient={gradient} x={240} y={290} w={170} h={70} title={box('backend')} />
            <Box gradient={gradient} x={10} y={255} w={170} h={50} title={box('browser')} />
            <Box gradient={gradient} x={10} y={325} w={170} h={66} title={box('clients')} />
            <Box
              gradient={gradient}
              x={700}
              y={290}
              w={170}
              h={70}
              title={box('cloudModels')}
              tone="cloud"
            />
            <text
              x={785}
              y={384}
              textAnchor="middle"
              fill="var(--uncertain)"
              fontSize={13}
              fontWeight={600}
            >
              {box('cloud')}
            </text>
            <Arrow marker={marker} d={line(180, 282, 238, 308)} />
            <Arrow marker={marker} d={line(180, 355, 238, 342)} />
            <Arrow marker={marker} d={line(325, 288, 325, 116)} />
            <Arrow marker={marker} d={line(412, 300, 468, 90)} />
            <Arrow marker={marker} d={line(412, 308, 468, 170)} />
            <Arrow marker={marker} d={line(412, 316, 468, 245)} />
            <Arrow
              marker={marker}
              tone="cloud"
              d={line(412, 335, 698, 335)}
              label={box('redactedOnly')}
              labelX={555}
              labelY={326}
            />
          </>
        )}
      </Drawing>

      <Drawing
        id="containers"
        variant="narrow"
        title={title}
        description={description}
        viewBox="0 0 340 590"
      >
        {({ gradient, marker }) => (
          <>
            <Box gradient={gradient} x={10} y={8} w={150} h={70} title={box('browser')} />
            <Box gradient={gradient} x={180} y={8} w={150} h={70} title={box('clients')} />
            <rect
              x={10}
              y={122}
              width={312}
              height={348}
              rx={16}
              fill="var(--verified)"
              fillOpacity={0.06}
              stroke="var(--verified)"
              strokeDasharray="6 5"
              strokeWidth={2}
            />
            <text x={24} y={460} fill="var(--verified)" fontSize={14} fontWeight={600}>
              {box('privateNetwork')}
            </text>
            <Box gradient={gradient} x={90} y={158} w={160} h={56} title={box('backend')} />
            <Box gradient={gradient} x={24} y={254} w={120} h={64} title={box('sources')} />
            <Box gradient={gradient} x={196} y={254} w={120} h={64} title={box('database')} />
            <Box
              gradient={gradient}
              x={24}
              y={346}
              w={120}
              h={90}
              title={box('local')}
              note={box('localNote')}
              tone="local"
            />
            <Box gradient={gradient} x={196} y={354} w={120} h={64} title={box('pdf')} />
            <Box
              gradient={gradient}
              x={20}
              y={494}
              w={160}
              h={70}
              title={box('cloudModels')}
              tone="cloud"
            />
            <text
              x={100}
              y={582}
              textAnchor="middle"
              fill="var(--uncertain)"
              fontSize={13}
              fontWeight={600}
            >
              {box('cloud')}
            </text>
            <Arrow marker={marker} d={line(85, 78, 120, 156)} />
            <Arrow marker={marker} d={line(255, 78, 220, 156)} />
            {/* The bus: the backend reaches the four private services from one trunk. */}
            <path
              d={line(170, 214, 170, 386)}
              fill="none"
              stroke="var(--muted-foreground)"
              strokeWidth={1.75}
            />
            <Arrow marker={marker} d={line(170, 286, 146, 286)} />
            <Arrow marker={marker} d={line(170, 286, 194, 286)} />
            <Arrow marker={marker} d={line(170, 386, 146, 386)} />
            <Arrow marker={marker} d={line(170, 386, 194, 386)} />
            {/* The only way out of the private network: redacted text to the cloud models. */}
            <Arrow
              marker={marker}
              tone="cloud"
              d="M 250 186 L 334 186 L 334 529 L 182 529"
              label={box('redactedOnly')}
              labelX={330}
              labelY={519}
              labelAnchor="end"
            />
          </>
        )}
      </Drawing>
    </Figure>
  )
}

const STEPS = [
  'intake',
  'ocr',
  'redact',
  'classify',
  'retrieve',
  'rules',
  'risk',
  'verify',
  'correct',
  'compose',
] as const

interface Layout {
  columns: number
  w: number
  h: number
  gapX: number
  rowHeight: number
  x0: number
  y0: number
}

function PipelineSteps({
  layout,
  gradient,
  marker,
  language,
}: {
  layout: Layout
  gradient: string
  marker: string
  language: 'bn' | 'en'
}) {
  const { t } = useTranslation()
  const { columns, w, h, gapX, rowHeight, x0, y0 } = layout
  const pos = (index: number) => ({
    x: x0 + (index % columns) * (w + gapX),
    y: y0 + Math.floor(index / columns) * rowHeight,
  })
  return (
    <>
      {STEPS.slice(0, -1).map((step, index) => {
        const from = pos(index)
        const to = pos(index + 1)
        if (index % columns === columns - 1) {
          // End of a row: down, across and down into the first box of the next row.
          const mid = from.y + h + (rowHeight - h) / 2
          return (
            <Arrow
              key={step}
              marker={marker}
              d={`M ${String(from.x + w / 2)} ${String(from.y + h)} L ${String(from.x + w / 2)} ${String(mid)} L ${String(to.x + w / 2)} ${String(mid)} L ${String(to.x + w / 2)} ${String(to.y - 2)}`}
            />
          )
        }
        return (
          <Arrow
            key={step}
            marker={marker}
            d={line(from.x + w, from.y + h / 2, to.x - 3, to.y + h / 2)}
          />
        )
      })}
      {STEPS.map((step, index) => {
        const { x, y } = pos(index)
        const local = step === 'ocr' || step === 'redact'
        return (
          <g key={step}>
            <Box
              gradient={gradient}
              x={x}
              y={y}
              w={w}
              h={h}
              title={t(`docs.architecture.stage.${step}`)}
              tone={local ? 'local' : 'plain'}
            />
            <text x={x + 6} y={y - 6} fill="var(--muted-foreground)" fontSize={13}>
              {localizeDigits(String(index + 1), language)}
            </text>
          </g>
        )
      })}
    </>
  )
}

export function PipelineDiagram() {
  const { t, i18n } = useTranslation()
  const language = i18n.language === 'en' ? 'en' : 'bn'
  const title = t('docs.architecture.pipelineTitle')
  const description = t('docs.architecture.pipelineDesc')
  return (
    <Figure description={description}>
      <Drawing
        id="pipeline"
        variant="wide"
        title={title}
        description={description}
        viewBox="0 0 910 236"
      >
        {({ gradient, marker }) => (
          <PipelineSteps
            gradient={gradient}
            marker={marker}
            language={language}
            layout={{ columns: 5, w: 160, h: 56, gapX: 22, rowHeight: 120, x0: 10, y0: 28 }}
          />
        )}
      </Drawing>
      <Drawing
        id="pipeline"
        variant="narrow"
        title={title}
        description={description}
        viewBox="0 0 340 520"
      >
        {({ gradient, marker }) => (
          <PipelineSteps
            gradient={gradient}
            marker={marker}
            language={language}
            layout={{ columns: 2, w: 145, h: 58, gapX: 30, rowHeight: 98, x0: 10, y0: 28 }}
          />
        )}
      </Drawing>
    </Figure>
  )
}
