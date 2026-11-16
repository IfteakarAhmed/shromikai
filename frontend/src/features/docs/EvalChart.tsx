import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useTranslation } from 'react-i18next'
import type { Language } from '../../i18n'
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
    <figure>
      <figcaption className="mb-2 font-semibold">{t('docs.evaluation.chartTitle')}</figcaption>
      <div aria-hidden="true" className="h-80 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={rows}
            layout="vertical"
            margin={{ top: 4, right: 16, left: 8, bottom: 0 }}
          >
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
            <XAxis
              type="number"
              allowDecimals={false}
              tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }}
              tickFormatter={(v: number) => formatNumber(v, language)}
            />
            <YAxis
              type="category"
              dataKey="label"
              width={170}
              tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }}
            />
            <Tooltip
              contentStyle={{
                background: 'var(--card)',
                border: '1px solid var(--border)',
                borderRadius: 8,
                color: 'var(--foreground)',
              }}
            />
            <Bar dataKey="found" fill="var(--verified)" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </figure>
  )
}
