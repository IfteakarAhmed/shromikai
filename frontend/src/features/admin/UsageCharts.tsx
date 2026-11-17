import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import type { UsageReport } from '../../api/admin'
import { GlassCard } from '../../components/ui/GlassCard'
import {
  axisTick,
  chartGrid,
  legendFormatter,
  markEdge,
  seriesColor,
  singleSeries,
  tooltipProps,
  usageSeries,
} from '../../lib/chartTheme'
import { formatDay, formatDecimal, formatNumber, formatUsd } from '../../lib/format'
import type { Language } from '../../i18n'

/**
 * The two charts of `/admin/usage` (FR-73). Recharts is a chunk of its own, loaded only when an
 * administrator opens the page (ADR-0023). The charts are a picture of the table beside them; the
 * table carries every number for a screen reader.
 */
export default function UsageCharts({
  days,
  language,
}: {
  days: UsageReport['days']
  language: Language
}) {
  const { t } = useTranslation()
  const rows = days.map((day) => ({ ...day, label: formatDay(day.date, language) }))
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <GlassCard depth="still" className="p-4">
        <figure>
          <figcaption className="mb-2 font-semibold">{t('admin.usage.chartPerDay')}</figcaption>
          <div aria-hidden="true" className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                accessibilityLayer={false}
                data={rows}
                margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
              >
                <CartesianGrid {...chartGrid} />
                <XAxis dataKey="label" tick={axisTick} minTickGap={12} />
                <YAxis
                  allowDecimals={false}
                  tick={axisTick}
                  width={36}
                  tickFormatter={(v: number) => formatNumber(v, language)}
                />
                <Tooltip {...tooltipProps} />
                <Legend formatter={legendFormatter} />
                {usageSeries.map((series) => (
                  <Bar
                    key={series.key}
                    dataKey={series.key}
                    name={t(`admin.usage.${series.key}`)}
                    stackId="a"
                    fill={seriesColor(series.key)}
                    stroke={markEdge}
                  />
                ))}
              </BarChart>
            </ResponsiveContainer>
          </div>
        </figure>
      </GlassCard>
      <GlassCard depth="still" className="p-4">
        <figure>
          <figcaption className="mb-2 font-semibold">{t('admin.usage.chartCost')}</figcaption>
          <div aria-hidden="true" className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                accessibilityLayer={false}
                data={rows}
                margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
              >
                <CartesianGrid {...chartGrid} />
                <XAxis dataKey="label" tick={axisTick} minTickGap={12} />
                <YAxis
                  tick={axisTick}
                  width={48}
                  tickFormatter={(v: number) => formatDecimal(v, language, 2)}
                />
                <Tooltip {...tooltipProps} formatter={(v) => formatUsd(Number(v), language)} />
                <Bar
                  dataKey="costUsd"
                  name={t('admin.usage.cost')}
                  fill={singleSeries}
                  radius={[4, 4, 0, 0]}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </figure>
      </GlassCard>
    </div>
  )
}
