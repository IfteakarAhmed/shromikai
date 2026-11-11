# Synthetic contracts (P5 fixtures)

Five **synthetic** employment contracts (3 Bangla, 2 English) for the privacy and intake tests and
for the `dev` split of the evaluation set `contracts` (docs/spec/evaluation.md).

- Every name, address, number and e-mail address in these files is **invented**. No real person,
  company or contract was used (R-02). Each document starts with a line that says so.
- `{id}.meta.yml`: provenance, planted risks and the expected clause count.
- `{id}.entities.yaml`: every personal identifier in the document, with its `PiiType`.
- Each contract repeats a content marker (`ZQXJ-CANARY-…`) in three clauses, so tests can prove that
  no contract text reaches storage, logs or a cloud request.
- The planted risks are one-sided commercial terms; the files cite no statute. What the law says
  comes only from the ingested corpus (R-10).

Real contracts never go into this repository.
