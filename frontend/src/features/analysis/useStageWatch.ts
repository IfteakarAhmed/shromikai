import { useEffect, useRef, useState } from 'react'
import type { Problem } from '../../api/client'
import { watchEvents, type PollResult, type Stage } from '../../api/sse'
import { useAuthedCall } from '../../api/useAuthedCall'
import { useAuth } from '../../auth/authContext'
import { watchTiming } from './timing'

export interface WatchState {
  seen: Stage[]
  progress: number
  warnings: { code: string; message: string }[]
  polling: boolean
  failure: Problem | null
  gone: boolean
}

const INITIAL: WatchState = {
  seen: [],
  progress: 0,
  warnings: [],
  polling: false,
  failure: null,
  gone: false,
}

interface Options {
  url: string
  enabled: boolean
  language: string
  poll: (token: string | null, language: string) => Promise<PollResult>
  onCompleted: () => void
}

/**
 * Follows the stages of a running job through the SSE client and keeps what the progress screen
 * shows: the stages seen, the percentage, warnings, whether it fell back to polling, and how it
 * failed. `onCompleted` runs when the stream or the polling says the job is done.
 */
export function useStageWatch({ url, enabled, language, poll, onCompleted }: Options): WatchState {
  const [state, setState] = useState<WatchState>(INITIAL)
  const call = useAuthedCall()
  const auth = useAuth()
  // The latest functions, so a renewed token or a new render does not restart the stream.
  const latest = useRef({ call, auth, poll, onCompleted, language })
  useEffect(() => {
    latest.current = { call, auth, poll, onCompleted, language }
  })

  useEffect(() => {
    if (!enabled) {
      return undefined
    }
    const controller = new AbortController()
    void watchEvents({
      url,
      language: latest.current.language,
      getToken: () => latest.current.auth.getToken(),
      refreshToken: () => latest.current.auth.renewToken(),
      poll: () => latest.current.call((token, lang) => latest.current.poll(token, lang)),
      signal: controller.signal,
      pollIntervalMs: watchTiming.pollIntervalMs,
      retryDelayMs: watchTiming.retryDelayMs,
      onEvent: (event) => {
        if (event.name === 'stage') {
          setState((current) => ({
            ...current,
            seen: current.seen.includes(event.stage)
              ? current.seen
              : [...current.seen, event.stage],
            progress: Math.max(current.progress, event.progress),
            polling: event.id === 0,
          }))
        } else if (event.name === 'warning') {
          setState((current) => ({
            ...current,
            warnings: [...current.warnings, { code: event.code, message: event.message }],
          }))
        }
      },
    }).then((outcome) => {
      if (controller.signal.aborted) {
        return
      }
      if (outcome.kind === 'completed') {
        latest.current.onCompleted()
      } else if (outcome.kind === 'failed') {
        setState((current) => ({ ...current, failure: outcome.problem }))
      } else if (outcome.kind === 'gone') {
        setState((current) => ({ ...current, gone: true }))
      }
    })
    return () => {
      controller.abort()
    }
  }, [enabled, url])

  return state
}
