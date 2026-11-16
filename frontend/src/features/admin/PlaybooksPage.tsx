import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import {
  archivePlaybook,
  createPlaybook,
  listPlaybooks,
  updatePlaybook,
  type Playbook,
  type PlaybookInput,
  type PlaybookRule,
} from '../../api/admin'
import { useAuthedCall } from '../../api/useAuthedCall'
import { useAuth } from '../../auth/authContext'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon } from '../../components/ui/Icon'
import { Skeleton } from '../../components/ui/Skeleton'
import { CLAUSE_TYPES, COMPARATORS, FACTS, FACT_UNITS, SEVERITIES } from '../../lib/enums'
import { formatNumber } from '../../lib/format'
import { useTitle } from '../../lib/useTitle'
import type { Language } from '../../i18n'

/** A rule while it is typed: the value is text so an empty box stays empty. */
interface RuleDraft {
  clauseType: PlaybookRule['clauseType']
  fact: PlaybookRule['fact']
  comparator: PlaybookRule['comparator']
  value: string
  unit: NonNullable<PlaybookRule['unit']>
  severity: PlaybookRule['severity']
  messageBn: string
  messageEn: string
  statuteSectionKey: string
}

interface Draft {
  id: string | null
  name: string
  side: 'WORKER' | 'EMPLOYER'
  rules: RuleDraft[]
}

function blankRule(): RuleDraft {
  return {
    clauseType: 'WORKING_HOURS',
    fact: 'DAILY_WORKING_HOURS',
    comparator: 'MAX',
    value: '',
    unit: 'HOURS_PER_DAY',
    severity: 'MEDIUM',
    messageBn: '',
    messageEn: '',
    statuteSectionKey: '',
  }
}

function toDraft(playbook: Playbook): Draft {
  return {
    id: playbook.id,
    name: playbook.name,
    side: playbook.side === 'EMPLOYER' ? 'EMPLOYER' : 'WORKER',
    rules: playbook.rules.map((rule) => ({
      clauseType: rule.clauseType,
      fact: rule.fact,
      comparator: rule.comparator,
      value:
        rule.value === null || rule.comparator === 'REQUIRED' || rule.comparator === 'PROHIBITED'
          ? ''
          : String(rule.value),
      unit: rule.unit ?? 'COUNT',
      severity: rule.severity,
      messageBn: rule.messageBn,
      messageEn: rule.messageEn,
      statuteSectionKey: rule.statuteSectionKey ?? '',
    })),
  }
}

/** The request body. A rule that only needs presence carries neither a value nor a unit. */
function toInput(draft: Draft): PlaybookInput {
  return {
    name: draft.name.trim(),
    side: draft.side,
    rules: draft.rules.map((rule) => {
      const needsValue = rule.comparator !== 'REQUIRED' && rule.comparator !== 'PROHIBITED'
      const out: Record<string, unknown> = {
        clauseType: rule.clauseType,
        fact: rule.fact,
        comparator: rule.comparator,
        severity: rule.severity,
        messageBn: rule.messageBn.trim(),
        messageEn: rule.messageEn.trim(),
        statuteSectionKey:
          rule.statuteSectionKey.trim() === '' ? null : rule.statuteSectionKey.trim(),
      }
      if (needsValue) {
        out['value'] = Number(rule.value)
        out['unit'] = rule.unit
      }
      return out as unknown as PlaybookRule
    }),
  }
}

/**
 * Playbooks (FR-57): an organisation's own rules, shown to its users as policy and never as law.
 * Used at `/admin/playbooks` and, for HR, at `/app/playbooks`.
 */
export default function PlaybooksPage() {
  const { t, i18n } = useTranslation()
  useTitle(t('admin.playbooks.title'))
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const call = useAuthedCall()
  const client = useQueryClient()
  const { session } = useAuth()
  const [draft, setDraft] = useState<Draft | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const list = useQuery({
    queryKey: ['playbooks'],
    queryFn: () => call((token, lang) => listPlaybooks(lang, token)),
    gcTime: 5 * 60 * 1000,
  })
  const save = useMutation({
    mutationFn: (value: Draft) =>
      call((token, lang) =>
        value.id === null
          ? createPlaybook(toInput(value), lang, token)
          : updatePlaybook(value.id, toInput(value), lang, token),
      ),
    onSuccess: () => {
      setDraft(null)
      setNotice(t('admin.playbooks.saved'))
      void client.invalidateQueries({ queryKey: ['playbooks'] })
    },
  })
  const archive = useMutation({
    mutationFn: (id: string) => call((token, lang) => archivePlaybook(id, lang, token)),
    onSuccess: () => {
      setNotice(t('admin.playbooks.archived'))
      void client.invalidateQueries({ queryKey: ['playbooks'] })
    },
  })
  const hasOrg = session.status === 'authenticated' && session.user.org !== null

  return (
    <div>
      <h2 className="text-2xl font-bold">{t('admin.playbooks.title')}</h2>
      <p className="mt-1 text-muted-foreground">{t('admin.playbooks.intro')}</p>
      {notice !== null && (
        <p role="status" className="mt-3 font-semibold text-verified">
          {notice}
        </p>
      )}
      {!hasOrg && session.status === 'authenticated' && (
        <p className="mt-3 text-muted-foreground">{t('admin.playbooks.noOrg')}</p>
      )}

      {draft === null ? (
        <>
          {hasOrg && (
            <Button
              className="mt-4"
              onClick={() => {
                setNotice(null)
                setDraft({ id: null, name: '', side: 'WORKER', rules: [blankRule()] })
              }}
            >
              <Icon name="plus" />
              {t('admin.playbooks.new')}
            </Button>
          )}
          {archive.isError && (
            <div className="mt-4">
              <ErrorState error={archive.error} />
            </div>
          )}
          <div className="mt-5">
            {list.isPending && (
              <div role="status" aria-label={t('common.loading')} className="space-y-3">
                <Skeleton className="h-20 w-full" />
                <Skeleton className="h-20 w-full" />
              </div>
            )}
            {list.isError && (
              <ErrorState
                error={list.error}
                onRetry={() => {
                  void list.refetch()
                }}
              />
            )}
            {list.data !== undefined && list.data.items.length === 0 && (
              <GlassCard>
                <EmptyState
                  title={t('admin.playbooks.empty')}
                  description={t('admin.playbooks.emptyHelp')}
                  icon="book"
                />
              </GlassCard>
            )}
            {list.data !== undefined && list.data.items.length > 0 && (
              <ul className="grid gap-4 md:grid-cols-2">
                {list.data.items
                  .filter((playbook) => playbook.archivedAt === null)
                  .map((playbook) => (
                    <li key={playbook.id}>
                      <GlassCard className="h-full p-5">
                        <h3 className="text-lg font-bold">{playbook.name}</h3>
                        <p className="mt-1 text-muted-foreground">
                          {t(
                            `admin.playbooks.sides.${playbook.side === 'EMPLOYER' ? 'EMPLOYER' : 'WORKER'}`,
                          )}
                          {' · '}
                          {t('admin.playbooks.rules', {
                            count: formatNumber(playbook.rules.length, language),
                          })}
                          {' · '}
                          {t('admin.playbooks.version', {
                            version: formatNumber(playbook.version, language),
                          })}
                        </p>
                        <div className="mt-4 flex flex-wrap gap-2">
                          <Button
                            variant="outline"
                            onClick={() => {
                              setNotice(null)
                              setDraft(toDraft(playbook))
                            }}
                          >
                            {t('admin.playbooks.edit')}
                            <span className="sr-only"> · {playbook.name}</span>
                          </Button>
                          <Button
                            variant="outline"
                            busy={archive.isPending && archive.variables === playbook.id}
                            onClick={() => {
                              if (window.confirm(t('admin.playbooks.archiveConfirm'))) {
                                archive.mutate(playbook.id)
                              }
                            }}
                          >
                            <Icon name="trash" />
                            {t('admin.playbooks.archive')}
                            <span className="sr-only"> · {playbook.name}</span>
                          </Button>
                        </div>
                      </GlassCard>
                    </li>
                  ))}
              </ul>
            )}
          </div>
        </>
      ) : (
        <PlaybookForm
          draft={draft}
          busy={save.isPending}
          error={save.isError ? save.error : null}
          onChange={setDraft}
          onCancel={() => {
            setDraft(null)
            save.reset()
          }}
          onSubmit={() => {
            save.mutate(draft)
          }}
        />
      )}
    </div>
  )
}

const inputClass =
  'mt-1 block min-h-11 w-full rounded-lg border border-input bg-background/60 px-3 py-2'

function PlaybookForm({
  draft,
  busy,
  error,
  onChange,
  onCancel,
  onSubmit,
}: {
  draft: Draft
  busy: boolean
  error: unknown
  onChange: (draft: Draft) => void
  onCancel: () => void
  onSubmit: () => void
}) {
  const { t } = useTranslation()
  const [touched, setTouched] = useState(false)

  function setRule(index: number, patch: Partial<RuleDraft>) {
    onChange({
      ...draft,
      rules: draft.rules.map((rule, i) => (i === index ? { ...rule, ...patch } : rule)),
    })
  }

  const problems = {
    name: draft.name.trim().length < 2,
    rules: draft.rules.length === 0,
    rule: draft.rules.map((rule) => ({
      value:
        rule.comparator !== 'REQUIRED' &&
        rule.comparator !== 'PROHIBITED' &&
        (rule.value.trim() === '' || Number.isNaN(Number(rule.value)) || Number(rule.value) < 0),
      messageBn: rule.messageBn.trim() === '',
      messageEn: rule.messageEn.trim() === '',
    })),
  }
  const invalid =
    problems.name ||
    problems.rules ||
    problems.rule.some((rule) => rule.value || rule.messageBn || rule.messageEn)

  function submit(event: SyntheticEvent) {
    event.preventDefault()
    setTouched(true)
    if (invalid) {
      window.setTimeout(() => {
        document.querySelector<HTMLElement>('form [aria-invalid="true"]')?.focus()
      }, 0)
      return
    }
    onSubmit()
  }

  const required = (flag: boolean, id: string) =>
    touched && flag ? (
      <p id={id} className="mt-1 font-semibold text-rejected">
        {t('admin.playbooks.required')}
      </p>
    ) : null

  return (
    <form onSubmit={submit} noValidate className="mt-5 space-y-6">
      <GlassCard className="space-y-4 p-5">
        <div>
          <label htmlFor="pb-name" className="font-semibold">
            {t('admin.playbooks.name')}
          </label>
          <input
            id="pb-name"
            value={draft.name}
            maxLength={80}
            aria-invalid={touched && problems.name ? true : undefined}
            aria-describedby={touched && problems.name ? 'pb-name-error' : undefined}
            onChange={(event) => {
              onChange({ ...draft, name: event.target.value })
            }}
            className={inputClass}
          />
          {required(problems.name, 'pb-name-error')}
        </div>
        <div>
          <label htmlFor="pb-side" className="font-semibold">
            {t('admin.playbooks.side')}
          </label>
          <select
            id="pb-side"
            value={draft.side}
            onChange={(event) => {
              onChange({
                ...draft,
                side: event.target.value === 'EMPLOYER' ? 'EMPLOYER' : 'WORKER',
              })
            }}
            className={inputClass}
          >
            <option value="WORKER">{t('admin.playbooks.sides.WORKER')}</option>
            <option value="EMPLOYER">{t('admin.playbooks.sides.EMPLOYER')}</option>
          </select>
        </div>
      </GlassCard>

      <fieldset className="space-y-4">
        <legend className="text-xl font-bold">{t('admin.playbooks.rulesLegend')}</legend>
        {touched && problems.rules && (
          <p className="font-semibold text-rejected">{t('admin.playbooks.noRules')}</p>
        )}
        {draft.rules.map((rule, index) => {
          const n = String(index + 1)
          const units = FACT_UNITS[rule.fact]
          const needsValue = rule.comparator !== 'REQUIRED' && rule.comparator !== 'PROHIBITED'
          const p = problems.rule[index]
          return (
            <GlassCard key={index} className="space-y-4 p-5">
              <div className="flex items-center justify-between gap-3">
                <h3 className="text-lg font-bold">{t('admin.playbooks.rule', { n })}</h3>
                <Button
                  variant="outline"
                  onClick={() => {
                    onChange({ ...draft, rules: draft.rules.filter((_, i) => i !== index) })
                  }}
                >
                  <Icon name="trash" />
                  {t('admin.playbooks.removeRule', { n })}
                </Button>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <div>
                  <label htmlFor={`pb-clause-${n}`} className="font-semibold">
                    {t('admin.playbooks.clauseType')}
                  </label>
                  <select
                    id={`pb-clause-${n}`}
                    value={rule.clauseType}
                    onChange={(event) => {
                      setRule(index, {
                        clauseType: event.target.value,
                      })
                    }}
                    className={inputClass}
                  >
                    {CLAUSE_TYPES.map((type) => (
                      <option key={type} value={type}>
                        {t(`clauseType.${type}`)}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor={`pb-fact-${n}`} className="font-semibold">
                    {t('admin.playbooks.fact')}
                  </label>
                  <select
                    id={`pb-fact-${n}`}
                    value={rule.fact}
                    onChange={(event) => {
                      const fact = event.target.value as RuleDraft['fact']
                      const allowed = FACT_UNITS[fact]
                      setRule(index, {
                        fact,
                        unit: allowed.includes(rule.unit) ? rule.unit : (allowed[0] ?? rule.unit),
                      })
                    }}
                    className={inputClass}
                  >
                    {FACTS.map((fact) => (
                      <option key={fact} value={fact}>
                        {t(`fact.${fact}`)}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor={`pb-comparator-${n}`} className="font-semibold">
                    {t('admin.playbooks.comparator')}
                  </label>
                  <select
                    id={`pb-comparator-${n}`}
                    value={rule.comparator}
                    onChange={(event) => {
                      setRule(index, {
                        comparator: event.target.value as RuleDraft['comparator'],
                      })
                    }}
                    className={inputClass}
                  >
                    {COMPARATORS.map((comparator) => (
                      <option key={comparator} value={comparator}>
                        {t(`comparator.${comparator}`)}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor={`pb-severity-${n}`} className="font-semibold">
                    {t('admin.playbooks.severity')}
                  </label>
                  <select
                    id={`pb-severity-${n}`}
                    value={rule.severity}
                    onChange={(event) => {
                      setRule(index, { severity: event.target.value as RuleDraft['severity'] })
                    }}
                    className={inputClass}
                  >
                    {SEVERITIES.map((severity) => (
                      <option key={severity} value={severity}>
                        {t(`severity.${severity.toLowerCase()}`)}
                      </option>
                    ))}
                  </select>
                </div>
                {needsValue && (
                  <>
                    <div>
                      <label htmlFor={`pb-value-${n}`} className="font-semibold">
                        {t('admin.playbooks.value')}
                      </label>
                      <input
                        id={`pb-value-${n}`}
                        inputMode="decimal"
                        value={rule.value}
                        aria-invalid={touched && p?.value === true ? true : undefined}
                        aria-describedby={
                          touched && p?.value === true ? `pb-value-${n}-error` : undefined
                        }
                        onChange={(event) => {
                          setRule(index, { value: event.target.value })
                        }}
                        className={inputClass}
                      />
                      {required(p?.value === true, `pb-value-${n}-error`)}
                    </div>
                    <div>
                      <label htmlFor={`pb-unit-${n}`} className="font-semibold">
                        {t('admin.playbooks.unit')}
                      </label>
                      <select
                        id={`pb-unit-${n}`}
                        value={rule.unit}
                        onChange={(event) => {
                          setRule(index, { unit: event.target.value as RuleDraft['unit'] })
                        }}
                        className={inputClass}
                      >
                        {units.map((unit) => (
                          <option key={unit} value={unit}>
                            {t(`unit.${unit}`)}
                          </option>
                        ))}
                      </select>
                    </div>
                  </>
                )}
              </div>
              <div>
                <label htmlFor={`pb-bn-${n}`} className="font-semibold">
                  {t('admin.playbooks.messageBn')}
                </label>
                <input
                  id={`pb-bn-${n}`}
                  lang="bn"
                  maxLength={300}
                  value={rule.messageBn}
                  aria-invalid={touched && p?.messageBn === true ? true : undefined}
                  aria-describedby={
                    touched && p?.messageBn === true ? `pb-bn-${n}-error` : undefined
                  }
                  onChange={(event) => {
                    setRule(index, { messageBn: event.target.value })
                  }}
                  className={inputClass}
                />
                {required(p?.messageBn === true, `pb-bn-${n}-error`)}
              </div>
              <div>
                <label htmlFor={`pb-en-${n}`} className="font-semibold">
                  {t('admin.playbooks.messageEn')}
                </label>
                <input
                  id={`pb-en-${n}`}
                  lang="en"
                  maxLength={300}
                  value={rule.messageEn}
                  aria-invalid={touched && p?.messageEn === true ? true : undefined}
                  aria-describedby={
                    touched && p?.messageEn === true ? `pb-en-${n}-error` : undefined
                  }
                  onChange={(event) => {
                    setRule(index, { messageEn: event.target.value })
                  }}
                  className={inputClass}
                />
                {required(p?.messageEn === true, `pb-en-${n}-error`)}
              </div>
              <div>
                <label htmlFor={`pb-section-${n}`} className="font-semibold">
                  {t('admin.playbooks.sectionKey')}
                </label>
                <input
                  id={`pb-section-${n}`}
                  dir="ltr"
                  lang="en"
                  value={rule.statuteSectionKey}
                  onChange={(event) => {
                    setRule(index, { statuteSectionKey: event.target.value })
                  }}
                  className={inputClass}
                />
              </div>
            </GlassCard>
          )
        })}
        <Button
          variant="outline"
          onClick={() => {
            onChange({ ...draft, rules: [...draft.rules, blankRule()] })
          }}
        >
          <Icon name="plus" />
          {t('admin.playbooks.addRule')}
        </Button>
      </fieldset>

      {error !== null && <ErrorState error={error} />}
      <div className="flex flex-wrap gap-3">
        <Button type="submit" busy={busy}>
          {t('admin.playbooks.save')}
        </Button>
        <Button variant="outline" onClick={onCancel}>
          {t('admin.playbooks.cancel')}
        </Button>
      </div>
    </form>
  )
}
