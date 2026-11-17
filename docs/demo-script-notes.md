# Demo script notes (honest-reporting rules)

Notes for whoever writes and presents the demo script in P12. The script itself lives in
`docs/submission/` (not written yet). Rules R-07 and R-12 apply to every line of it.

## Known weak card: the transfer risk (BLA-2006:3)

The transfer-risk card in the recorded demo shows `BLA-2006:3` as `VERIFIED`, and that citation does
not clearly support the claim. This card is the documented example of the same-family verifier
limitation: the verifier is a second Claude prompt (`SAME_FAMILY`), so it judges "does this section
support this claim" with the same blind spots as the model that proposed the citation. Our own
cross-check of 17 November found it. We have not changed the recordings, the verifier or the UI to
hide it. Fix planned after 20 November: a cross-family verifier (a Gemini Flash run or another
independent model, [ADR-0018](adr/0018-gemini-verifier-dropped-for-now.md)) plus a
relevance check. Until then, do not call the verifier "independent", and when this card is shown,
say so.

What to say on stage if this card comes up: "This one is verified by our own check, but we looked at
it ourselves and the quote does not clearly support the claim. It is a limit of using the same model
family as the verifier. We found it, we show it, and a cross-family verifier is the planned fix."
Do not skip the card, do not call it a false alarm, and do not claim an independent verifier.

## Other things to say plainly

- Every demo answer is a **recorded** replay on a synthetic sample contract (profile `fake-models`).
- The obligations list and the memo summary of the recorded demo are scripted stand-ins
  (see [known gaps](known-gaps.md)).
- The 5 s Slow 3G figure is a target; we measured about 4.7 s load under emulation, as a proxy only
  (PRD NFR-10 note).
- Contract evaluation numbers are shown only after P10 writes `eval/reports/`; until then say
  "not yet run".
