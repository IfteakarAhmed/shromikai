import { useTranslation } from 'react-i18next'
import { LinkButton } from '../../components/ui/Button'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon, type IconName } from '../../components/ui/Icon'
import { useTitle } from '../../lib/useTitle'
import { HeroFrame } from './HeroFrame'

const STEPS: readonly { key: string; icon: IconName }[] = [
  { key: 'upload', icon: 'upload' },
  { key: 'privacy', icon: 'lock' },
  { key: 'review', icon: 'eye' },
  { key: 'verify', icon: 'shield' },
]

const TRUST: readonly { key: string; icon: IconName }[] = [
  { key: 'noInventedLaw', icon: 'shield' },
  { key: 'privateStored', icon: 'lock' },
  { key: 'officialLink', icon: 'external' },
]

/** `/`: the problem, how it works, the sample and the one-click demos (FR-68). */
export function LandingPage() {
  const { t } = useTranslation()
  useTitle(t('hero.eyebrow'))
  return (
    <>
      <section
        aria-labelledby="hero-title"
        className="relative overflow-hidden border-b border-border"
      >
        <div className="decor texture-grid absolute inset-0" aria-hidden="true" />
        <div
          className="decor absolute inset-0 bg-gradient-to-br from-hero-tint to-transparent"
          aria-hidden="true"
        />
        <div className="relative mx-auto grid max-w-6xl items-center gap-10 px-(--gutter) py-12 lg:grid-cols-2 lg:py-20">
          <div>
            <p className="font-semibold text-primary">{t('hero.eyebrow')}</p>
            <h1 id="hero-title" className="mt-3 text-4xl font-bold leading-tight sm:text-5xl">
              {t('hero.title')}
            </h1>
            <p className="mt-4 text-lg text-muted-foreground">{t('hero.description')}</p>
            <p className="mt-2 font-semibold">{t('hero.scope')}</p>
            <div className="mt-6 flex flex-col gap-3 sm:flex-row">
              <LinkButton to="/app" state={{ autoSample: true }} size="lg">
                <Icon name="play" />
                {t('hero.sample')}
              </LinkButton>
              <LinkButton to="/app" size="lg" variant="secondary">
                <Icon name="upload" />
                {t('hero.own')}
              </LinkButton>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <LinkButton to="/demo-user" variant="outline">
                {t('hero.workerDemo')}
              </LinkButton>
              <LinkButton to="/demo-hr" variant="outline">
                {t('hero.hrDemo')}
              </LinkButton>
              <LinkButton to="/demo-admin" variant="outline">
                {t('hero.adminDemo')}
              </LinkButton>
            </div>
          </div>
          <HeroFrame alt={t('hero.sceneAlt')} />
        </div>
      </section>

      <section aria-label={t('trust.label')} className="border-b border-border">
        <ul className="mx-auto grid max-w-6xl gap-3 px-(--gutter) py-6 md:grid-cols-3">
          {TRUST.map(({ key, icon }) => (
            <li key={key} className="flex items-center gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-verified/10 text-verified">
                <Icon name={icon} />
              </span>
              <span className="font-medium">{t(`trust.${key}`)}</span>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="how-title" className="mx-auto max-w-6xl px-(--gutter) py-14">
        <p className="font-semibold text-primary">{t('howItWorks.eyebrow')}</p>
        <h2 id="how-title" className="mt-1 text-3xl font-bold">
          {t('howItWorks.title')}
        </h2>
        <p className="mt-2 max-w-2xl text-muted-foreground">{t('howItWorks.description')}</p>
        <ol className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {STEPS.map(({ key, icon }, index) => (
            <li key={key}>
              <GlassCard className="h-full p-5">
                <div className="flex items-center gap-3">
                  <span className="flex size-10 items-center justify-center rounded-full bg-primary text-primary-foreground">
                    <Icon name={icon} />
                  </span>
                  <span className="text-sm text-muted-foreground">
                    {t('howItWorks.stepNumber', { number: t(`howItWorks.digit${index + 1}`) })}
                  </span>
                </div>
                <h3 className="mt-3 text-xl font-bold">{t(`howItWorks.${key}`)}</h3>
                <p className="mt-1 text-muted-foreground">{t(`howItWorks.${key}Body`)}</p>
              </GlassCard>
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="compare-title" className="border-y border-border bg-raised/50">
        <div className="mx-auto grid max-w-6xl items-center gap-6 px-(--gutter) py-12 md:grid-cols-[1fr_auto]">
          <div>
            <p className="font-semibold text-primary">{t('comparison.eyebrow')}</p>
            <h2 id="compare-title" className="mt-1 text-3xl font-bold">
              {t('comparison.title')}
            </h2>
            <p className="mt-2 max-w-2xl text-muted-foreground">{t('comparison.description')}</p>
          </div>
          <LinkButton to="/app/compare" size="lg" variant="secondary">
            <Icon name="columns" />
            {t('comparison.cta')}
          </LinkButton>
        </div>
      </section>

      <section aria-labelledby="audience-title" className="mx-auto max-w-6xl px-(--gutter) py-14">
        <p className="font-semibold text-primary">{t('audience.eyebrow')}</p>
        <h2 id="audience-title" className="mt-1 text-3xl font-bold">
          {t('audience.title')}
        </h2>
        <ul className="mt-8 grid gap-4 md:grid-cols-3">
          {(
            [
              ['workers', '/demo-user'],
              ['hr', '/demo-hr'],
              ['ngo', '/demo-admin'],
            ] as const
          ).map(([key, to]) => (
            <li key={key}>
              <GlassCard className="flex h-full flex-col p-5">
                <h3 className="text-xl font-bold">{t(`audience.${key}`)}</h3>
                <p className="mt-1 flex-1 text-muted-foreground">{t(`audience.${key}Body`)}</p>
                <LinkButton to={to} variant="outline" className="mt-4">
                  {t(`audience.${key}Cta`)}
                </LinkButton>
              </GlassCard>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="limits-title" className="border-t border-border bg-raised/50">
        <div className="mx-auto max-w-6xl px-(--gutter) py-12">
          <p className="font-semibold text-primary">{t('limits.eyebrow')}</p>
          <h2 id="limits-title" className="mt-1 text-3xl font-bold">
            {t('limits.title')}
          </h2>
          <ul className="mt-4 flex flex-wrap gap-3">
            <li className="rounded-lg border border-uncertain/40 bg-uncertain/10 px-4 py-2 font-semibold">
              {t('limits.notAdvice')}
            </li>
            <li className="rounded-lg border border-border px-4 py-2 font-semibold">
              {t('limits.uncertain')}
            </li>
          </ul>
          <p className="mt-4 max-w-3xl text-muted-foreground">{t('limits.description')}</p>
        </div>
      </section>
    </>
  )
}
