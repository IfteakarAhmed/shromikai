import { flushSync } from 'react-dom'
import {
  NavigationType,
  UNSAFE_createBrowserHistory,
  type HistoryRouterProps,
} from 'react-router-dom'

type History = HistoryRouterProps['history']

type TransitionDocument = Document & { startViewTransition?: (update: () => void) => unknown }

/** Back and Forward may be animated: the browser can, motion is allowed, low bandwidth is off. */
function mayTransition(): boolean {
  return (
    typeof (document as TransitionDocument).startViewTransition === 'function' &&
    !window.matchMedia('(prefers-reduced-motion: reduce)').matches &&
    document.documentElement.dataset['lowbw'] !== 'true'
  )
}

/**
 * The browser history of the app, with one addition: when the visitor presses Back or Forward the
 * router is told inside a View Transition (C4), like a link click already is (C1). The router
 * reads the history, so the change is held until the browser has taken its picture of the old
 * page; the update runs synchronously inside the transition. Pushes and replaces are left alone
 * (links start their own transition).
 *
 * The router component is `unstable_HistoryRouter` with `useTransitions` off, so the router's own
 * update is synchronous inside `flushSync`. Its name says unstable: its name says unstable, its contract (a
 * `History`) is small, and a change would show in the tests below and in `e2e/c4.spec.ts`.
 */
export function createTransitionHistory(): History {
  const history = UNSAFE_createBrowserHistory({ v5Compat: true })
  const listen = history.listen.bind(history)
  history.listen = (listener) =>
    listen((update) => {
      if (update.action !== NavigationType.Pop || !mayTransition()) {
        listener(update)
        return
      }
      ;(document as TransitionDocument).startViewTransition(() => {
        flushSync(() => {
          listener(update)
        })
      })
    })
  return history
}
