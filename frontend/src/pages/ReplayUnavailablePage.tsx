import { useTranslation } from 'react-i18next'
import { LinkButton } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { useTitle } from '../lib/useTitle'

/** What a feature that needs a live server shows in the static recorded replay (FR-85, R-12). */
export function ReplayUnavailablePage() {
  const { t } = useTranslation()
  useTitle(t('replay.unavailableTitle'))
  return (
    <section className="mx-auto max-w-xl px-(--gutter) py-12">
      <GlassCard depth="still" className="p-8 text-center">
        <h1 className="text-3xl">{t('replay.unavailableTitle')}</h1>
        <p className="mt-4 text-lg text-muted-foreground">{t('replay.unavailableBody')}</p>
        <div className="mt-6">
          <LinkButton to="/demo-user">{t('replay.toSample')}</LinkButton>
        </div>
      </GlassCard>
    </section>
  )
}
