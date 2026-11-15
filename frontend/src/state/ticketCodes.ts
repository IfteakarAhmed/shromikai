/**
 * Secret codes of review tickets, kept in the memory of this page load only (R-02): the result
 * shows the code once, and the ticket page can use it while the page stays open. Reloading forgets
 * it on purpose; the reader enters it again.
 */
const codes = new Map<string, string>()

export function rememberTicketCode(ticketId: string, code: string): void {
  codes.set(ticketId, code)
}

export function ticketCode(ticketId: string): string | null {
  return codes.get(ticketId) ?? null
}
