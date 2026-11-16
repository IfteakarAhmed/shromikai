import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { localizeDigits } from '../../lib/format'

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

function Box({ x, y, w, h, title, note, tone = 'plain' }: BoxProps) {
  const stroke =
    tone === 'cloud' ? 'var(--uncertain)' : tone === 'local' ? 'var(--verified)' : 'var(--border)'
  const lines = wrap(title, Math.max(8, Math.floor(w / 9.5)))
  const noteLines = note === undefined ? [] : wrap(note, Math.max(8, Math.floor(w / 7)))
  const total = lines.length * 18 + noteLines.length * 15
  const top = y + (h - total) / 2 + 14
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
      {lines.map((line, i) => (
        <text
          key={`t${String(i)}`}
          x={x + w / 2}
          y={top + i * 18}
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
          y={top + lines.length * 18 + i * 15}
          textAnchor="middle"
          fill="var(--muted-foreground)"
          fontSize={12}
        >
          {line}
        </text>
      ))}
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
      viewBox="0 0 880 410"
    >
      {(marker) => (
        <>
          <rect
            x={200}
            y={10}
            width={470}
            height={390}
            rx={14}
            fill="none"
            stroke="var(--verified)"
            strokeDasharray="6 5"
            strokeWidth={2}
          />
          <text x={216} y={32} fill="var(--verified)" fontSize={13} fontWeight={600}>
            {t('docs.architecture.privateNetwork')}
          </text>
          <Box x={240} y={50} w={170} h={64} title={t('docs.architecture.sources')} />
          <Box x={470} y={45} w={170} h={64} title={t('docs.architecture.database')} />
          <Box
            x={470}
            y={125}
            w={170}
            h={76}
            title={t('docs.architecture.local')}
            note={t('docs.architecture.localNote')}
            tone="local"
          />
          <Box x={470} y={215} w={170} h={50} title={t('docs.architecture.pdf')} />
          <Box x={240} y={290} w={170} h={70} title={t('docs.architecture.backend')} />
          <Box x={10} y={255} w={170} h={50} title={t('docs.architecture.browser')} />
          <Box x={10} y={325} w={170} h={66} title={t('docs.architecture.clients')} />
          <Box
            x={700}
            y={290}
            w={170}
            h={70}
            title={t('docs.architecture.cloudModels')}
            tone="cloud"
          />
          <text
            x={785}
            y={382}
            textAnchor="middle"
            fill="var(--uncertain)"
            fontSize={12}
            fontWeight={600}
          >
            {t('docs.architecture.cloud')}
          </text>
          <Arrow x1={180} y1={282} x2={238} y2={308} marker={marker} />
          <Arrow x1={180} y1={355} x2={238} y2={342} marker={marker} />
          <Arrow x1={325} y1={288} x2={325} y2={116} marker={marker} />
          <Arrow x1={412} y1={300} x2={468} y2={90} marker={marker} />
          <Arrow x1={412} y1={308} x2={468} y2={170} marker={marker} />
          <Arrow x1={412} y1={316} x2={468} y2={245} marker={marker} />
          <Arrow
            x1={412}
            y1={335}
            x2={698}
            y2={335}
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
  const { t, i18n } = useTranslation()
  const language = i18n.language === 'en' ? 'en' : 'bn'
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
                  {localizeDigits(String(index + 1), language)}
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
