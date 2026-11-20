import { Suspense, lazy, useId, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { BrandMark } from '../../components/brand/BrandMark'
import { LinkButton } from '../../components/ui/Button'
import { GlassCard } from '../../components/ui/GlassCard'
import { DisplayText } from '../../components/ui/DisplayText'
import { Icon, type IconName } from '../../components/ui/Icon'
import { LiveLinkButton, LiveOnlyNote } from '../../components/ui/LiveOnly'
import { isStaticReplay } from '../../api/replay'
import { cx } from '../../lib/cx'
import { useTitle } from '../../lib/useTitle'
import { HeroFrame } from './HeroFrame'
import { SceneToggle } from './SceneToggle'
import type { Chapter } from './cinematic/timeline'
import { useSceneTier } from './cinematic/useSceneTier'

// The cinematic scene (three.js and the shaders) is fetched only on the top tier (ADR-0023).
const CinematicScene = lazy(() => import('./cinematic/CinematicScene'))

const TRUST: readonly { key: string; icon: IconName }[] = [
  { key: 'noInventedLaw', icon: 'shield' },
  { key: 'privateStored', icon: 'lock' },
  { key: 'officialLink', icon: 'external' },
]

/** The middle chapters: an icon and the keys of their text (FR-68). */
const STORY: readonly { chapter: Chapter; icon: IconName }[] = [
  { chapter: 'privacy', icon: 'lock' },
  { chapter: 'search', icon: 'book' },
  { chapter: 'verify', icon: 'check' },
  { chapter: 'invented', icon: 'x' },
]

function ChapterText({
  chapter,
  icon,
  cinematic,
  children,
}: {
  chapter: Chapter
  icon: IconName
  cinematic: boolean
  children?: ReactNode
}) {
  const { t } = useTranslation()
  return (
    <div
      className={cx(
        cinematic
          ? 'max-w-xl rounded-2xl border border-border bg-background/80 p-6 shadow-glass'
          : 'grid gap-4 md:grid-cols-[auto_1fr]',
      )}
    >
      <span
        className={cx(
          'flex size-12 shrink-0 items-center justify-center rounded-xl',
          chapter === 'invented' ? 'bg-rejected/15 text-rejected' : 'bg-verified/12 text-verified',
        )}
      >
        <Icon name={icon} size={24} />
      </span>
      <div className={cinematic ? 'mt-4' : undefined}>
        <p className="font-semibold text-primary">{t(`story.${chapter}Eyebrow`)}</p>
        <h2 id={`${chapter}-title`} className="mt-1 text-3xl font-bold leading-tight">
          <DisplayText text={t(`story.${chapter}Title`)} />
        </h2>
        <p className="mt-3 max-w-2xl text-lg text-muted-foreground">{t(`story.${chapter}Body`)}</p>
        {children}
      </div>
    </div>
  )
}

/**
 * The call to action of one audience card. The HR and admin demos sign in to a live server, so on
 * the static replay they are disabled with a note (FR-85); the worker demo works.
 */
function AudienceCta({ to, children }: { to: string; children: ReactNode }) {
  const note = useId()
  return (
    <>
      <LiveLinkButton
        to={to}
        noteId={note}
        variant="outline"
        className="mt-4"
        needsServer={to !== '/demo-user'}
      >
        {children}
      </LiveLinkButton>
      {to !== '/demo-user' && <LiveOnlyNote id={note} />}
    </>
  )
}

/**
 * `/`: the story of one contract in six chapters (FR-68). The chapters are real HTML in every tier,
 * Bangla first; on the cinematic tier a fixed WebGL scene behind them follows the native scroll, on
 * the standard tier the hero frame has the smaller scene, and on the lite tier a poster rendered
 * from the scene (scripts/render-scene.mjs). A switch keeps the still picture one click away.
 */
export function LandingPage() {
  const heroNote = useId()
  const finaleNote = useId()
  const { t } = useTranslation()
  useTitle(t('hero.eyebrow'))
  const { tier, optIn, choose, fail } = useSceneTier()
  const cinematic = tier === 'cinematic'
  const pinned = (extra?: string) =>
    cx('relative', cinematic ? 'min-h-[175vh]' : 'border-b border-border', extra)
  const stickyBox = cinematic
    ? 'sticky top-[max(6rem,calc(50vh-12rem))] mx-auto max-w-6xl px-(--gutter) py-10'
    : 'mx-auto max-w-6xl px-(--gutter) py-12'

  return (
    <div data-tier={tier} className="relative">
      {cinematic && (
        <>
          <Suspense fallback={null}>
            <CinematicScene onFailure={fail} />
          </Suspense>
          <div
            aria-hidden="true"
            className="pointer-events-none fixed inset-0 z-0 bg-gradient-to-r from-background/90 via-background/45 to-transparent"
          />
        </>
      )}

      <div data-story className="relative z-10">
        <section
          data-chapter="hero"
          aria-labelledby="hero-title"
          className={cx(
            'relative overflow-hidden border-b border-border',
            cinematic && 'flex min-h-[calc(100svh-4rem)] items-center border-b-0',
          )}
        >
          {!cinematic && (
            <>
              <div className="decor texture-grid absolute inset-0" aria-hidden="true" />
              <div
                className="decor absolute inset-0 bg-gradient-to-br from-hero-tint to-transparent"
                aria-hidden="true"
              />
            </>
          )}
          <div
            className={cx(
              'relative mx-auto grid w-full max-w-6xl items-center gap-10 px-(--gutter) py-12 lg:py-20',
              !cinematic && 'lg:grid-cols-2',
            )}
          >
            <div className={cinematic ? 'max-w-2xl' : undefined}>
              <div className="flex items-center gap-3">
                <BrandMark labelled className="size-14 sm:size-16" />
                <p className="font-semibold text-primary">{t('hero.eyebrow')}</p>
              </div>
              <h1
                id="hero-title"
                className={cx(
                  'mt-3 font-bold leading-tight',
                  cinematic ? 'text-5xl sm:text-6xl' : 'text-4xl sm:text-5xl',
                )}
              >
                <DisplayText text={t('hero.title')} />
              </h1>
              <p className="mt-4 text-lg text-muted-foreground">{t('hero.description')}</p>
              <p className="mt-2 font-semibold">{t('hero.scope')}</p>
              <div className="mt-6 flex flex-col gap-3 sm:flex-row">
                <LinkButton to="/app" state={{ autoSample: true }} size="lg">
                  <Icon name="play" />
                  {t('hero.sample')}
                </LinkButton>
                <LiveLinkButton to="/app" noteId={heroNote} size="lg" variant="secondary">
                  <Icon name="upload" />
                  {t('hero.own')}
                </LiveLinkButton>
              </div>
              <div className="mt-4 flex flex-wrap gap-2">
                <LinkButton to="/demo-user" variant="outline">
                  {t('hero.workerDemo')}
                </LinkButton>
                <LiveLinkButton to="/demo-hr" noteId={heroNote} variant="outline">
                  {t('hero.hrDemo')}
                </LiveLinkButton>
                <LiveLinkButton to="/demo-admin" noteId={heroNote} variant="outline">
                  {t('hero.adminDemo')}
                </LiveLinkButton>
              </div>
              <LiveOnlyNote id={heroNote} className="max-w-xl" />
              {!cinematic && (
                <SceneToggle
                  tier={tier}
                  canEnable={optIn !== null}
                  onChoose={choose}
                  floating={false}
                />
              )}
              <ul aria-label={t('trust.label')} className="mt-8 grid gap-3">
                {TRUST.map(({ key, icon }) => (
                  <li key={key} className="flex items-center gap-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-verified/10 text-verified">
                      <Icon name={icon} />
                    </span>
                    <span className="font-medium">{t(`trust.${key}`)}</span>
                  </li>
                ))}
              </ul>
            </div>
            {!cinematic && (
              <HeroFrame alt={t('hero.sceneAlt')} mode={tier === 'standard' ? 'scene' : 'poster'} />
            )}
          </div>
        </section>

        {STORY.map(({ chapter, icon }) => (
          <section
            key={chapter}
            data-chapter={chapter}
            aria-labelledby={`${chapter}-title`}
            className={pinned()}
          >
            <div className={stickyBox}>
              <ChapterText chapter={chapter} icon={icon} cinematic={cinematic}>
                {chapter === 'invented' && (
                  <>
                    <p className="mt-4 inline-flex rounded-lg border border-border px-3 py-1 text-sm font-semibold">
                      {t('story.illustration')}
                    </p>
                    {/* Compare needs a live server: the static replay leaves the button out. */}
                    {!isStaticReplay() && (
                      <div className="mt-5">
                        <LinkButton to="/app/compare" variant="secondary">
                          <Icon name="columns" />
                          {t('comparison.cta')}
                        </LinkButton>
                      </div>
                    )}
                  </>
                )}
              </ChapterText>
            </div>
          </section>
        ))}

        <section
          data-chapter="finale"
          aria-labelledby="finale-title"
          className={cx(
            'relative',
            cinematic ? 'flex min-h-[130vh] items-center' : 'border-b border-border',
          )}
        >
          <div className="mx-auto w-full max-w-6xl px-(--gutter) py-14">
            <div
              className={cx(
                'mx-auto max-w-2xl text-center',
                cinematic && 'rounded-2xl border border-border bg-background/80 p-8 shadow-glass',
              )}
            >
              <p className="font-semibold text-primary">{t('story.finaleEyebrow')}</p>
              <h2 id="finale-title" className="mt-1 text-4xl font-bold leading-tight">
                <DisplayText text={t('story.finaleTitle')} />
              </h2>
              <p className="mt-3 text-lg text-muted-foreground">{t('story.finaleBody')}</p>
              <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
                <LinkButton to="/app" state={{ autoSample: true }} size="lg">
                  <Icon name="play" />
                  {t('story.finaleSample')}
                </LinkButton>
                <LiveLinkButton to="/app" noteId={finaleNote} size="lg" variant="secondary">
                  <Icon name="upload" />
                  {t('story.finaleOwn')}
                </LiveLinkButton>
              </div>
              <LiveOnlyNote id={finaleNote} className="mx-auto max-w-xl text-left" />
            </div>
          </div>
        </section>
      </div>

      <div className="relative z-10 bg-background">
        <section aria-labelledby="audience-title" className="mx-auto max-w-6xl px-(--gutter) py-14">
          <p className="font-semibold text-primary">{t('audience.eyebrow')}</p>
          <h2 id="audience-title" className="mt-1 text-3xl font-bold">
            <DisplayText text={t('audience.title')} />
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
                  <AudienceCta to={to}>{t(`audience.${key}Cta`)}</AudienceCta>
                </GlassCard>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="limits-title" className="border-t border-border bg-raised/50">
          <div className="mx-auto max-w-6xl px-(--gutter) py-12">
            <p className="font-semibold text-primary">{t('limits.eyebrow')}</p>
            <h2 id="limits-title" className="mt-1 text-3xl font-bold">
              <DisplayText text={t('limits.title')} />
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
      </div>

      {cinematic && <SceneToggle tier={tier} canEnable onChoose={choose} floating />}
    </div>
  )
}
