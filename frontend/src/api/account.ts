import { apiGet, apiSend } from './client'
import type { components } from './types'

export type Me = components['schemas']['Me']
export type StoredProfile = components['schemas']['StoredProfile']
export type ReviewTicketStatus = components['schemas']['ReviewTicketStatus']

export function getMe(language: string, token: string | null): Promise<Me> {
  return apiGet<Me>('/api/v1/auth/me', language, token ?? undefined)
}

/** Saves the profile of the account (E-09); it changes wording only. */
export function saveProfile(
  profile: StoredProfile,
  language: string,
  token: string | null,
): Promise<Me> {
  return apiSend<Me>(
    'PATCH',
    '/api/v1/auth/me',
    language,
    { profile, locale: null },
    token ?? undefined,
  )
}

/** The status of a review ticket (E-22); the secret code travels in a header, never in the URL. */
export function getTicket(
  ticketId: string,
  ticketToken: string,
  language: string,
  token: string | null,
): Promise<ReviewTicketStatus> {
  return apiGet<ReviewTicketStatus>(
    `/api/v1/review-tickets/${encodeURIComponent(ticketId)}`,
    language,
    token ?? undefined,
    { 'X-Ticket-Token': ticketToken },
  )
}
