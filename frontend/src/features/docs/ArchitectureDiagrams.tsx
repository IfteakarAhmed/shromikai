import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * The two architecture pictures of `/docs/architecture` (FR-74), drawn by hand as SVG: no diagram
 * library and no generated image. Colours come from the theme tokens, so both themes and both
 * languages read the same. Boxes and steps follow `docs/spec/architecture.md` §3 and §5.1.
 */

interface BoxProps {
  x: number
  y: number
  w: number
  h: number
  title: string
  note?: string
  tone?: 'plain' | 'local' | 'cloud'
}

function Box({ x, y, w, h, title, note, tone = 'plain' }: BoxProps) {
  const stroke =
    tone === 'cloud' ? 'var(--uncertain)' : tone === 'local' ? 'var(--verified)' : 'var(--border)'
  return (
    <g>
      <rect
        x={x}
        y={y}
        width={w}
        height={h}
        rx={10}
        fill="var(--card)"
        stroke={stroke}
        strokeWidth={tone === 'plain' ? 1.5 : 2.5}
      />
      <text
        x={x + w / 2}
        y={y + (note === undefined ? h / 2 + 5 : h / 2 - 4)}
        textAnchor="middle"
        fill="var(--foreground)"
        fontSize={15}
        fontWeight={600}
      >
        {title}
      </text>
      {note !== undefined && (
        <text
          x={x + w / 2}
          y={y + h / 2 + 15}
          textAnchor="middle"
          fill="var(--muted-foreground)"
          fontSize={12}
        >
          {note}
        </text>
      )}
    </g>
  )
}

function Arrow({
  x1,
  y1,
  x2,
  y2,
  label,
  marker,
}: {
  x1: number
  y1: number
  x2: number
  y2: number
  label?: string
  marker: string
}) {
  return (
    <g>
      <line
        x1={x1}
        y1={y1}
        x2={x2}
        y2={y2}
        stroke="var(--muted-foreground)"
        strokeWidth={1.5}
        markerEnd={`url(#${marker})`}
      />
      {label !== undefined && (
        <text
          x={(x1 + x2) / 2}
          y={(y1 + y2) / 2 - 6}
          textAnchor="middle"
          fill="var(--muted-foreground)"
          fontSize={12}
        >
          {label}
        </text>
      )}
    </g>
  )
}

function Figure({
  id,
  title,
  description,
  viewBox,
  children,
}: {
  id: string
  title: string
  description: string
  viewBox: string
  children: (marker: string) => ReactNode
}) {
  return (
    <figure className="glass-panel rounded-xl p-3">
      <svg
        role="img"
        aria-labelledby={`${id}-title`}
        aria-describedby={`${id}-desc`}
        viewBox={viewBox}
        className="h-auto w-full"
        fontFamily="inherit"
      >
        <title id={`${id}-title`}>{title}</title>
        <desc id={`${id}-desc`}>{description}</desc>
        <defs>
          <marker
            id={`${id}-arrow`}
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="7"
            markerHeight="7"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--muted-foreground)" />
          </marker>
        </defs>
        {children(`${id}-arrow`)}
      </svg>
      <figcaption className="mt-2 px-1 text-sm text-muted-foreground">{description}</figcaption>
    </figure>
  )
}

export function ContainersDiagram() {
  const { t } = useTranslation()
  return (
    <Figure
      id="containers"
      title={t('docs.architecture.containersTitle')}
      description={t('docs.architecture.containersDesc')}
      viewBox="0 0 760 400"
    >
      {(marker) => (
        <>
          <rect
            x={150}
            y={10}
            width={420}
            height={380}
            rx={14}
            fill="none"
            stroke="var(--verified)"
            strokeDasharray="6 5"
            strokeWidth={2}
          />
          <text x={166} y={32} fill="var(--verified)" fontSize={13} fontWeight={600}>
            {t('docs.architecture.privateNetwork')}
          </text>
          <Box x={10} y={60} w={120} h={50} title={t('docs.architecture.browser')} />
          <Box x={10} y={130} w={120} h={60} title={t('docs.architecture.clients')} />
          <Box x={200} y={90} w={160} h={70} title={t('docs.architecture.backend')} />
          <Box x={420} y={50} w={130} h={64} title={t('docs.architecture.database')} />
          <Box
            x={420}
            y={140}
            w={130}
            h={64}
            title={t('docs.architecture.local')}
            note={t('docs.architecture.localNote')}
            tone="local"
          />
          <Box x={420} y={230} w={130} h={50} title={t('docs.architecture.pdf')} />
          <Box x={200} y={300} w={160} h={56} title={t('docs.architecture.sources')} />
          <Box
            x={620}
            y={150}
            w={130}
            h={64}
            title={t('docs.architecture.cloudModels')}
            tone="cloud"
          />
          <text
            x={685}
            y={238}
            textAnchor="middle"
            fill="var(--uncertain)"
            fontSize={12}
            fontWeight={600}
          >
            {t('docs.architecture.cloud')}
          </text>
          <Arrow x1={130} y1={85} x2={198} y2={115} marker={marker} />
          <Arrow x1={130} y1={160} x2={198} y2={140} marker={marker} />
          <Arrow x1={360} y1={105} x2={418} y2={85} marker={marker} />
          <Arrow x1={360} y1={125} x2={418} y2={170} marker={marker} />
          <Arrow x1={360} y1={145} x2={418} y2={250} marker={marker} />
          <Arrow x1={280} y1={162} x2={280} y2={298} marker={marker} />
          <Arrow
            x1={360}
            y1={130}
            x2={618}
            y2={182}
            label={t('docs.architecture.redactedOnly')}
            marker={marker}
          />
        </>
      )}
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

export function PipelineDiagram() {
  const { t } = useTranslation()
  const w = 170
  const h = 52
  const gap = 28
  const columns = 5
  const pos = (index: number) => ({
    x: 10 + (index % columns) * (w + gap),
    y: 20 + Math.floor(index / columns) * 110,
  })
  return (
    <Figure
      id="pipeline"
      title={t('docs.architecture.pipelineTitle')}
      description={t('docs.architecture.pipelineDesc')}
      viewBox="0 0 1030 230"
    >
      {(marker) => (
        <>
          {STEPS.map((step, index) => {
            const { x, y } = pos(index)
            const local = step === 'ocr' || step === 'redact'
            return (
              <g key={step}>
                <Box
                  x={x}
                  y={y}
                  w={w}
                  h={h}
                  title={t(`docs.architecture.stage.${step}`)}
                  tone={local ? 'local' : 'plain'}
                />
                <text x={x + 10} y={y - 5} fill="var(--muted-foreground)" fontSize={11}>
                  {index + 1}
                </text>
              </g>
            )
          })}
          {STEPS.slice(0, -1).map((step, index) => {
            const from = pos(index)
            const to = pos(index + 1)
            if (index % columns === columns - 1) {
              return (
                <path
                  key={step}
                  d={`M ${String(from.x + w / 2)} ${String(from.y + h)} L ${String(from.x + w / 2)} ${String(from.y + h + 20)} L ${String(to.x + w / 2)} ${String(from.y + h + 20)} L ${String(to.x + w / 2)} ${String(to.y)}`}
                  fill="none"
                  stroke="var(--muted-foreground)"
                  strokeWidth={1.5}
                  markerEnd={`url(#${marker})`}
                />
              )
            }
            return (
              <Arrow
                key={step}
                x1={from.x + w}
                y1={from.y + h / 2}
                x2={to.x - 2}
                y2={to.y + h / 2}
                marker={marker}
              />
            )
          })}
        </>
      )}
    </Figure>
  )
}
