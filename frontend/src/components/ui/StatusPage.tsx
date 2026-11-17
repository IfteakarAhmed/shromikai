import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { localizeDigits } from '../../lib/format'
import { GlassCard } from './GlassCard'

/**
 * The frame of the 403 and 404 pages (C4): one glass card with the status code large behind the
 * words (decoration only, hidden from assistive technology), the reason in plain words and the
 * ways out as buttons.
 */
export function StatusPage({
  code,
  title,
  headingId,
  children,
  actions,
}: {
  code: '403' | '404'
  title: string
  headingId: string
  children?: ReactNode
  actions: ReactNode
}) {
  const { i18n } = useTranslation()
  const language = i18n.language === 'en' ? 'en' : 'bn'
  return (
    <section aria-labelledby={headingId} className="mx-auto max-w-2xl px-(--gutter) py-12">
      <GlassCard depth="still" className="px-6 py-10 text-center sm:px-10">
        <p aria-hidden="true" className="status-code font-ui text-7xl font-bold sm:text-8xl">
          {localizeDigits(code, language)}
        </p>
        <h1 id={headingId} className="mt-2 text-3xl sm:text-4xl">
          {title}
        </h1>
        <div className="mx-auto mt-4 max-w-prose text-lg text-muted-foreground">{children}</div>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">{actions}</div>
      </GlassCard>
    </section>
  )
}
