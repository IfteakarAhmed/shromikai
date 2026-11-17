# ADR-0025: Generic Bangla role words from the local model are not redacted

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-17 |
| Deciders | Project owner (asked for this fix and its shape in the P10 follow-up), Claude (author) |
| Related | ADR-0003, ADR-0024; rules R-03, R-02, R-10; PRD FR-28, FR-29, FR-30; P10 known gap "Bangla contracts blocked by the egress guard" |

## Context

The local step (`pii-extract` on qwen2.5:7b-instruct) returns the names it finds. In P10 it also
returned generic role words as persons: "মালিক" (owner), "কর্মচারী" (employee), "স্বাক্ষর"
(signature). The redactor replaces an entity string only at word boundaries, so "মালিক" became
`[PERSON_n]` but the inflected forms "মালিকের", "মালিককে", "কর্মচারীর" stayed in the text. The egress
guard (stage 2) searches every request for every original value of the job's placeholder map as a
substring. "মালিক" is in the map, so the guard found it inside "মালিকের" and blocked the request
(`privacy-guard-blocked`). `syn-bn-01` and `syn-bn-03` failed for both systems. Nothing leaked; a
Bangla contract could not be analysed. The prompt itself lists "employer, worker" under persons,
which invites the labels.

## Decision

1. **A curated allowlist in a data file**, `data/privacy/role-words.yaml` (`bn` and `en` lists of
   generic party, role, title and signing words; authored and reviewed fields like the other data
   files, `status: draft` and `reviewed_by: null` until a team member who reads Bangla has read it).
   Path in `shromikai.privacy.role-words-file`. A missing, empty or malformed file stops start-up; an
   entry with digits, `@`, `/` or `:` is rejected so an identifier cannot be added by mistake.
2. **Applied only to whole values from the local model.** `RoleWordAllowlist` is called once, on the
   merged output of `LocalEntityExtractor`. An entity string that equals a listed word after NFC,
   trimming of edge punctuation and case folding is dropped. "মালিক রহিম নমুনা সরকার" is not a role
   word and is redacted in full; "মালিকের" is not on the list and is not matched.
3. **Nothing else changes.** The deterministic detectors (NID, phone, e-mail, passport, TIN, bank
   account, date of birth, ...) do not read the list. The egress guard does not read it either: it
   still blocks every original value that was redacted and every detector hit. The prompt is not
   changed (a prompt change is a new version with an eval report, AGENTS.md §5).

## Why this does not weaken R-03

R-03 protects identifiers: values that point to a person or an organisation. A generic role word
points to none; the word "মালিক" appears in every Bangla contract. Redacting it gained no privacy and
turned out to break availability, because the matcher cannot redact its inflected forms while the
guard cannot tell the difference. After the change the guard is exactly as strict as before for
every value that is protected, and a real name, NID, phone number or e-mail address that leaks is
still blocked (`BanglaRoleWordsFailClosedTest`). The failure mode of a wrong entry is bounded: a
missing word costs availability (the guard may block), an extra word only lets a generic word
through, and the file's rules forbid names, surnames and organisation names. A person whose own
name is a listed word (a real name "আব্দুল মালিক") is covered because the local model returns the
whole name, which is not a role word.

## Consequences

- Role words stay in the redacted text, so the cloud models read "মালিকের পক্ষে [PERSON_1]" instead
  of "[PERSON_2]-এর পক্ষে [PERSON_1]", which is better context for the risk agent.
- A role word that is also a part of a real name still gets a name-part placeholder when the model
  returns that name (`Redactor` splits persons into parts); the guard may then block an inflected
  form of it. That case fails closed, as before.
- The list is Bangla-first and short. A role word it lacks still fails closed; the fix is to add the
  word after review, never to relax the guard.
- The inflected forms are not listed: the guard works on protected values, and the model returns
  base words. If the model starts returning inflected forms, they are added to the file.
- Observation, not changed here: a lone name part (for example a surname) is redacted in the text
  but is not itself a protected value when it only occurred inside a longer name, so the guard
  would not block a lone surname that re-enters a request. Recorded in `docs/known-gaps.md`.

## Alternatives

- **Suffix stripping in the redactor** (redact "মালিক" inside "মালিকের"): Bangla morphology is
  irregular and a stemmer would redact parts of unrelated words; rejected.
- **Make the guard ignore short or common values**: weakens the guard for everything; rejected.
- **Change the prompt to forbid titles**: needs `pii-extract` v2, a dev-split report and a new live
  recording of the local step; the list is deterministic and testable now. May still be done later.
- **Hard-code the words in Java**: forbidden by the owner's instruction (data, not code) and harder
  for a Bangla reviewer to read.

## Verification

`BanglaRoleWordsFailClosedTest` (role words kept, real names, NID, phone and e-mail redacted and
blocked when leaked, no block on the redacted text), `RoleWordAllowlistTest` (whole-value matching,
file validation, no invented identifier of the five synthetic contracts is on the list), the local
evaluation step on all five contracts (`./gradlew evaluate -Pstep=local`, PII recall and role words
kept, no cloud call), and the live run of `syn-bn-01` recorded in `eval/reports/`.
