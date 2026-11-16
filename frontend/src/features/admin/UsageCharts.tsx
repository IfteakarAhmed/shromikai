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
import { formatDay, formatNumber, formatUsd } from '../../lib/format'
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
  const tick = { fill: 'var(--muted-foreground)', fontSize: 12 }
  const tooltip = {
    contentStyle: {
      background: 'var(--card)',
      border: '1px solid var(--border)',
      borderRadius: 8,
      color: 'var(--foreground)',
    },
  }
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <figure>
        <figcaption className="mb-2 font-semibold">{t('admin.usage.chartPerDay')}</figcaption>
        <div aria-hidden="true" className="h-72 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={rows} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
              <XAxis dataKey="label" tick={tick} minTickGap={12} />
              <YAxis
                allowDecimals={false}
                tick={tick}
                width={36}
                tickFormatter={(v: number) => formatNumber(v, language)}
              />
              <Tooltip {...tooltip} />
              <Legend />
              <Bar
                dataKey="analyses"
                name={t('admin.usage.analyses')}
                stackId="a"
                fill="var(--verified)"
              />
              <Bar
                dataKey="compares"
                name={t('admin.usage.compares')}
                stackId="a"
                fill="var(--sev-medium)"
              />
              <Bar
                dataKey="memoRenders"
                name={t('admin.usage.memoRenders')}
                stackId="a"
                fill="var(--uncertain)"
              />
              <Bar
                dataKey="apiCalls"
                name={t('admin.usage.apiCalls')}
                stackId="a"
                fill="var(--sev-high)"
              />
              <Bar
                dataKey="mcpCalls"
                name={t('admin.usage.mcpCalls')}
                stackId="a"
                fill="var(--sev-low)"
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </figure>
      <figure>
        <figcaption className="mb-2 font-semibold">{t('admin.usage.chartCost')}</figcaption>
        <div aria-hidden="true" className="h-72 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={rows} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
              <XAxis dataKey="label" tick={tick} minTickGap={12} />
              <YAxis tick={tick} width={56} tickFormatter={(v: number) => formatUsd(v, language)} />
              <Tooltip {...tooltip} formatter={(v) => formatUsd(Number(v), language)} />
              <Bar dataKey="costUsd" name={t('admin.usage.cost')} fill="var(--primary)" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </figure>
    </div>
  )
}
