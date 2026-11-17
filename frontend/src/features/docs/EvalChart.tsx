import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useTranslation } from 'react-i18next'
import type { Language } from '../../i18n'
import { GlassCard } from '../../components/ui/GlassCard'
import { axisTick, chartGrid, singleSeries, tooltipProps } from '../../lib/chartTheme'
import { formatNumber } from '../../lib/format'

/** Questions found in the first five results, per method; a picture of the table beside it. */
export default function EvalChart({
  data,
  language,
}: {
  data: { method: string; found: number; questions: number }[]
  language: Language
}) {
  const { t } = useTranslation()
  const rows = data.map((row) => ({
    ...row,
    label: t(`docs.evaluation.methods.${row.method}`, { defaultValue: row.method }),
  }))
  return (
    <GlassCard depth="still" className="p-4">
      <figure>
        <figcaption className="mb-2 font-semibold">{t('docs.evaluation.chartTitle')}</figcaption>
        <div aria-hidden="true" className="h-80 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              accessibilityLayer={false}
              data={rows}
              layout="vertical"
              margin={{ top: 4, right: 16, left: 8, bottom: 0 }}
            >
              <CartesianGrid {...chartGrid} />
              <XAxis
                type="number"
                allowDecimals={false}
                tick={axisTick}
                tickFormatter={(v: number) => formatNumber(v, language)}
              />
              <YAxis type="category" dataKey="label" width={170} tick={axisTick} />
              <Tooltip {...tooltipProps} />
              <Bar dataKey="found" fill={singleSeries} radius={[0, 4, 4, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </figure>
    </GlassCard>
  )
}
