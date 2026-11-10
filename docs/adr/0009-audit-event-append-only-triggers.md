# ADR-0009: `audit_event` is append-only through triggers, not grants

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name) |
| Related | T-15; FR-11, FR-78; architecture §11.3, §14, §17; migration `V2__foundation.sql`; P1 review fix 10 |

## Context

Threat T-15 (repudiation) needs an audit trail that the application cannot rewrite. Architecture
§11.3 said "the app role has `INSERT` and `SELECT` only", which assumes a separate, restricted
database role. In every profile today the application connects with one role that also owns the
schema, because Flyway runs inside the application at startup. `REVOKE UPDATE, DELETE` on a
table has no effect on its owner, so a grant-only design would look safe in the migration and
protect nothing at run time.

`V2__foundation.sql` instead enforces the rules with triggers: `UPDATE` and `TRUNCATE` always
fail, and `DELETE` succeeds only for rows older than 12 months (the retention job, architecture
§14). `ApplicationContextIT` checks both. The Phase 1 review asked for either grants or an ADR on
this approach.

## Decision

1. **Triggers are the enforcement mechanism for `audit_event`.** They hold for every role that
   does not own the triggers' functions or disable triggers, including the schema owner the
   application uses today.
2. **Grants come later as a second layer, not instead.** When P11 splits the migration role from
   the runtime role, the runtime role gets `INSERT, SELECT` on `audit_event` and `DELETE` only
   for the retention job; the triggers stay.
3. **Account deletion (FR-78, P9) anonymises through one narrow path.** Today no `UPDATE` can pass,
   so P9 adds a migration with a function `audit_event_anonymise(subject uuid)` declared
   `SECURITY DEFINER`. It sets a transaction-local flag (`SET LOCAL shromikai.audit_anonymise =
   'on'`) and runs `UPDATE audit_event SET actor_id = NULL WHERE actor_id = subject`, plus
   `target_id = NULL` where `target_type = 'USER'` and `target_id = subject::text`. The guard
   trigger then allows an `UPDATE` only when that flag is on **and** the new row equals the old
   row except that `actor_id` and/or `target_id` became `NULL`. Every other change still fails.
   `AccountDeletionIT` (FR-78) covers both the allowed and the refused updates.

## Consequences

- The append-only guarantee holds now, with the single database role, and is tested.
- A superuser or the table owner can still `ALTER TABLE ... DISABLE TRIGGER`. That needs DDL
  rights, which only migrations use; P11 removes them from the runtime role.
- Architecture §11.3 and T-15 now name the triggers instead of grants.

## Alternatives

- **Grants only, now.** Rejected: ineffective while the runtime role owns the table.
- **A separate migration role in P1.** Deferred to P11 hardening; it changes deployment and the
  local setup for every developer during the build week.
- **Never anonymise audit rows.** Rejected: FR-78 requires that no row references a deleted user.

## Verification

- `ApplicationContextIT`: `UPDATE`, young-row `DELETE` and `TRUNCATE` on `audit_event` fail.
- P9: `AccountDeletionIT` proves the anonymisation path and that any other update still fails.
- P11: a test that the runtime role cannot `UPDATE` `audit_event` even with the flag set outside
  the function.
