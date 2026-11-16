import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { createApiKey, listApiKeys, revokeApiKey, type CreatedKey } from '../../api/admin'
import { useAuthedCall } from '../../api/useAuthedCall'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon } from '../../components/ui/Icon'
import { Skeleton } from '../../components/ui/Skeleton'
import { formatDateTime } from '../../lib/format'
import { useTitle } from '../../lib/useTitle'
import type { Language } from '../../i18n'

const SCOPES = ['analyses', 'laws', 'mcp', 'automation'] as const

/**
 * `/admin/api-keys` (FR-65): create a key, see its secret once, revoke it. The secret is held in the
 * state of this page only; it is not stored, not cached and gone once the visitor leaves it.
 */
export default function ApiKeysPage() {
  const { t, i18n } = useTranslation()
  useTitle(t('admin.keys.title'))
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  const call = useAuthedCall()
  const client = useQueryClient()
  const [name, setName] = useState('')
  const [scopes, setScopes] = useState<string[]>(['analyses'])
  const [touched, setTouched] = useState(false)
  const [secret, setSecret] = useState<CreatedKey | null>(null)
  const [copied, setCopied] = useState(false)
  const list = useQuery({
    queryKey: ['admin', 'api-keys'],
    queryFn: () => call((token, lang) => listApiKeys(lang, token)),
    gcTime: 5 * 60 * 1000,
  })
  const create = useMutation({
    mutationFn: () => call((token, lang) => createApiKey(name.trim(), scopes, lang, token)),
    onSuccess: (created) => {
      setSecret(created)
      setCopied(false)
      setName('')
      setTouched(false)
      void client.invalidateQueries({ queryKey: ['admin', 'api-keys'] })
    },
  })
  const revoke = useMutation({
    mutationFn: (id: string) => call((token, lang) => revokeApiKey(id, lang, token)),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['admin', 'api-keys'] })
    },
  })

  const nameBad = name.trim().length < 1 || name.trim().length > 60
  const scopesBad = scopes.length === 0

  function submit(event: SyntheticEvent) {
    event.preventDefault()
    setTouched(true)
    if (nameBad || scopesBad) {
      return
    }
    create.mutate()
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div>
      <h2 className="text-2xl font-bold">{t('admin.keys.title')}</h2>
      <p className="mt-1 text-muted-foreground">{t('admin.keys.intro')}</p>

      {secret !== null && (
        <section
          aria-labelledby="secret-heading"
          className="mt-5 rounded-xl border border-uncertain/50 bg-uncertain/10 p-5"
        >
          <h3 id="secret-heading" className="text-lg font-bold">
            {t('admin.keys.secretTitle')}
          </h3>
          <p className="mt-1 font-semibold">{t('admin.keys.secretWarning')}</p>
          <p
            className="mt-3 break-all rounded-lg border border-border bg-background p-3"
            data-testid="api-key-secret"
          >
            <code lang="en" dir="ltr">
              {secret.key}
            </code>
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <Button
              variant="outline"
              onClick={() => {
                void copy(secret.key)
              }}
            >
              <Icon name="copy" />
              {t('admin.keys.copy')}
            </Button>
            {copied && (
              <span role="status" className="font-semibold text-verified">
                {t('admin.keys.copied')}
              </span>
            )}
            <Button
              onClick={() => {
                setSecret(null)
              }}
            >
              {t('admin.keys.done')}
            </Button>
          </div>
        </section>
      )}

      <form onSubmit={submit} noValidate className="mt-5">
        <GlassCard depth="still" className="space-y-4 p-5">
          <h3 className="text-lg font-bold">{t('admin.keys.create')}</h3>
          <div>
            <label htmlFor="key-name" className="font-semibold">
              {t('admin.keys.name')}
            </label>
            <input
              id="key-name"
              value={name}
              maxLength={60}
              aria-invalid={touched && nameBad ? true : undefined}
              aria-describedby={touched && nameBad ? 'key-name-error' : undefined}
              onChange={(event) => {
                setName(event.target.value)
              }}
              className="mt-1 block min-h-11 w-full rounded-lg border border-input bg-background/60 px-3 py-2"
            />
            {touched && nameBad && (
              <p id="key-name-error" className="mt-1 font-semibold text-rejected">
                {t('admin.playbooks.required')}
              </p>
            )}
          </div>
          <fieldset>
            <legend className="font-semibold">{t('admin.keys.scopes')}</legend>
            <div className="mt-1 grid gap-1 sm:grid-cols-2">
              {SCOPES.map((scope) => (
                <label key={scope} className="flex min-h-11 items-center gap-3">
                  <input
                    type="checkbox"
                    checked={scopes.includes(scope)}
                    onChange={(event) => {
                      setScopes((current) =>
                        event.target.checked
                          ? [...current, scope]
                          : current.filter((s) => s !== scope),
                      )
                    }}
                    className="size-5"
                  />
                  {t(`admin.keys.scopeNames.${scope}`)}
                </label>
              ))}
            </div>
            {touched && scopesBad && (
              <p className="mt-1 font-semibold text-rejected">{t('admin.keys.needScope')}</p>
            )}
          </fieldset>
          {create.isError && <ErrorState error={create.error} />}
          <Button type="submit" busy={create.isPending}>
            {t('admin.keys.submit')}
          </Button>
        </GlassCard>
      </form>

      <div className="mt-6">
        {list.isPending && (
          <div role="status" aria-label={t('common.loading')} className="space-y-3">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
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
        {revoke.isError && <ErrorState error={revoke.error} />}
        {list.data !== undefined && list.data.length === 0 && (
          <GlassCard>
            <EmptyState title={t('admin.keys.empty')} icon="key" />
          </GlassCard>
        )}
        {list.data !== undefined && list.data.length > 0 && (
          <div
            className="glass-panel overflow-x-auto rounded-xl"
            role="region"
            aria-label={t('admin.keys.caption')}
            tabIndex={0}
          >
            <table className="w-full border-collapse text-start">
              <caption className="sr-only">{t('admin.keys.caption')}</caption>
              <thead>
                <tr className="border-b border-border">
                  <th scope="col" className="px-4 py-3 text-start">
                    {t('admin.keys.colName')}
                  </th>
                  <th scope="col" className="px-4 py-3 text-start">
                    {t('admin.keys.prefix')}
                  </th>
                  <th scope="col" className="px-4 py-3 text-start">
                    {t('admin.keys.colScopes')}
                  </th>
                  <th scope="col" className="px-4 py-3 text-start">
                    {t('admin.keys.created')}
                  </th>
                  <th scope="col" className="px-4 py-3 text-start">
                    {t('admin.keys.lastUsed')}
                  </th>
                  <th scope="col" className="px-4 py-3 text-start">
                    {t('admin.keys.status')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {list.data.map((key) => {
                  const revoked = key.revokedAt !== null
                  return (
                    <tr key={key.id} className="border-b border-border last:border-b-0">
                      <th scope="row" className="px-4 py-3 text-start font-semibold">
                        {key.name}
                      </th>
                      <td className="px-4 py-3">
                        <code lang="en" dir="ltr">
                          {key.prefix}
                        </code>
                      </td>
                      <td className="px-4 py-3">
                        {new Intl.ListFormat(language, { style: 'narrow', type: 'unit' }).format(
                          key.scopes.map((scope) =>
                            t(`admin.keys.scopeNames.${scope}`, { defaultValue: scope }),
                          ),
                        )}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3">
                        {formatDateTime(key.createdAt, language)}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3">
                        {key.lastUsedAt === null
                          ? t('admin.keys.neverUsed')
                          : formatDateTime(key.lastUsedAt, language)}
                      </td>
                      <td className="px-4 py-3">
                        {revoked ? (
                          <Badge tone="rejected">{t('admin.keys.revoked')}</Badge>
                        ) : (
                          <div className="flex flex-wrap items-center gap-2">
                            <Badge tone="verified">{t('admin.keys.active')}</Badge>
                            <Button
                              variant="outline"
                              busy={revoke.isPending && revoke.variables === key.id}
                              onClick={() => {
                                if (window.confirm(t('admin.keys.revokeConfirm'))) {
                                  revoke.mutate(key.id)
                                }
                              }}
                            >
                              {t('admin.keys.revoke')}
                              <span className="sr-only"> · {key.name}</span>
                            </Button>
                          </div>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
