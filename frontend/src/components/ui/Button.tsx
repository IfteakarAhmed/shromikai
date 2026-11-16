import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Link, useNavigate, type LinkProps } from 'react-router-dom'
import { isPlainClick, withViewTransition } from '../../lib/viewTransition'
import { usePreferences } from '../../state/preferencesContext'
import { buttonClass, type ButtonSize, type ButtonVariant } from './buttonClass'
import { Icon } from './Icon'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  busy?: boolean
  children?: ReactNode
}

export function Button({
  variant = 'primary',
  size = 'md',
  busy = false,
  className,
  children,
  disabled,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={buttonClass(variant, size, className)}
      disabled={disabled === true || busy}
      aria-busy={busy || undefined}
      {...props}
    >
      {busy && <Icon name="loader" className="animate-spin" />}
      {children}
    </button>
  )
}

interface LinkButtonProps extends LinkProps {
  variant?: ButtonVariant
  size?: ButtonSize
}

/** A link that looks like a button; same-tab navigation runs inside a View Transition. */
export function LinkButton({
  variant = 'primary',
  size = 'md',
  className,
  onClick,
  ...props
}: LinkButtonProps) {
  const navigate = useNavigate()
  const { lowBandwidth } = usePreferences()
  return (
    <Link
      className={buttonClass(variant, size, className)}
      {...props}
      onClick={(event) => {
        onClick?.(event)
        if (!isPlainClick(event, props.target)) {
          return
        }
        event.preventDefault()
        withViewTransition(() => {
          void navigate(props.to, {
            state: props.state as unknown,
            replace: props.replace ?? false,
          })
        }, lowBandwidth)
      }}
    />
  )
}
