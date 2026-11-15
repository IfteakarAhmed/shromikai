import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { getMe, saveProfile, type Me, type StoredProfile } from '../../api/account'
import { useAuthedCall } from '../../api/useAuthedCall'
import { useAuth } from '../../auth/authContext'
import { Button, LinkButton } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Skeleton } from '../../components/ui/Skeleton'
import { formatNumber } from '../../lib/format'
import { useTitle } from '../../lib/useTitle'
import type { Language } from '../../i18n'

const SECTORS = [
  'READY_MADE_GARMENTS',
  'TEXTILES',
  'CONSTRUCTION',
  'IT_SOFTWARE',
  'RETAIL',
  'DOMESTIC_WORK',
  'SECURITY_SERVICES',
  'NGO',
  'BANKING_FINANCE',
  'OTHER',
] as const

const MAX_ROLE = 60

const selectClass =
  'mt-1 block min-h-11 w-full rounded-lg border border-input bg-background/60 px-3 py-2'

/** `/app/profile` (E-09): sector, job role, language and reading level. They change wording only. */
export function ProfilePage() {
  const { t } = useTranslation()
  useTitle(t('profile.title'))
  const { session } = useAuth()
  const call = useAuthedCall()
  const me = useQuery({
    queryKey: ['me'],
    enabled: session.status === 'authenticated',
    queryFn: () => call((token, lang) => getMe(lang, token)),
    retry: false,
  })

  if (session.status === 'guest') {
    return (
      <div className="mx-auto max-w-2xl px-(--gutter) py-10">
        <h1 className="text-3xl font-bold">{t('profile.title')}</h1>
        <p className="mt-3">{t('profile.guest')}</p>
        <div className="mt-4">
          <LinkButton to="/login">{t('profile.signIn')}</LinkButton>
        </div>
      </div>
    )
  }
  return (
    <div className="mx-auto max-w-2xl px-(--gutter) py-10">
      <h1 className="text-3xl font-bold">{t('profile.title')}</h1>
      <p className="mt-2 text-muted-foreground">{t('profile.intro')}</p>
      {me.isPending && (
        <div role="status" aria-label={t('common.loading')} className="mt-6 space-y-3">
          <Skeleton className="h-11 w-full" />
          <Skeleton className="h-11 w-full" />
        </div>
      )}
      {me.isError && (
        <div className="mt-6">
          <ErrorState
            error={me.error}
            onRetry={() => {
              void me.refetch()
            }}
          />
        </div>
      )}
      {me.isSuccess && <ProfileForm me={me.data} />}
    </div>
  )
}

function ProfileForm({ me }: { me: Me }) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const call = useAuthedCall()
  const client = useQueryClient()
  const stored = me.profile
  const [sector, setSector] = useState(stored?.sector ?? '')
  const [jobRole, setJobRole] = useState(stored?.jobRole ?? '')
  const [preferred, setPreferred] = useState(stored?.preferredLanguage ?? '')
  const [level, setLevel] = useState(stored?.readingLevel ?? '')

  const save = useMutation({
    mutationFn: () => {
      const profile: StoredProfile = {}
      if (sector !== '') {
        profile.sector = sector as StoredProfile['sector']
      }
      if (jobRole.trim() !== '') {
        profile.jobRole = jobRole.trim()
      }
      if (preferred !== '') {
        profile.preferredLanguage = preferred as StoredProfile['preferredLanguage']
      }
      if (level !== '') {
        profile.readingLevel = level as StoredProfile['readingLevel']
      }
      return call((token, lang) => saveProfile(profile, lang, token))
    },
    onSuccess: (updated) => {
      client.setQueryData(['me'], updated)
    },
  })

  return (
    <GlassCard className="mt-6 p-5">
      <form
        onSubmit={(event: SyntheticEvent) => {
          event.preventDefault()
          save.mutate()
        }}
        className="space-y-5"
      >
        <div>
          <label htmlFor="profile-sector" className="font-semibold">
            {t('profile.sector')}
          </label>
          <select
            id="profile-sector"
            value={sector}
            onChange={(event) => {
              setSector(event.target.value)
            }}
            className={selectClass}
          >
            <option value="">{t('profile.none')}</option>
            {SECTORS.map((value) => (
              <option key={value} value={value}>
                {t(`profile.sectors.${value}`)}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="profile-role" className="font-semibold">
            {t('profile.jobRole')}
          </label>
          <input
            id="profile-role"
            value={jobRole}
            maxLength={MAX_ROLE}
            onChange={(event) => {
              setJobRole(event.target.value)
            }}
            className={selectClass}
          />
          <p className="mt-1 text-sm text-muted-foreground">
            {t('profile.jobRoleHint', { max: formatNumber(MAX_ROLE, language) })}
          </p>
        </div>
        <div>
          <label htmlFor="profile-language" className="font-semibold">
            {t('profile.preferredLanguage')}
          </label>
          <select
            id="profile-language"
            value={preferred}
            onChange={(event) => {
              setPreferred(event.target.value)
            }}
            className={selectClass}
          >
            <option value="">{t('profile.none')}</option>
            <option value="bn">{t('language.bangla')}</option>
            <option value="en">{t('language.english')}</option>
          </select>
        </div>
        <div>
          <label htmlFor="profile-level" className="font-semibold">
            {t('profile.readingLevel')}
          </label>
          <select
            id="profile-level"
            value={level}
            onChange={(event) => {
              setLevel(event.target.value)
            }}
            className={selectClass}
          >
            <option value="">{t('profile.none')}</option>
            <option value="BASIC">{t('profile.level.BASIC')}</option>
            <option value="STANDARD">{t('profile.level.STANDARD')}</option>
          </select>
        </div>
        {save.isError && <ErrorState error={save.error} />}
        {save.isSuccess && (
          <p role="status" className="font-semibold text-verified">
            {t('profile.saved')}
          </p>
        )}
        <Button type="submit" size="lg" busy={save.isPending}>
          {save.isPending ? t('profile.saving') : t('profile.save')}
        </Button>
      </form>
    </GlassCard>
  )
}
