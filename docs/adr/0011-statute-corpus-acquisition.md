# ADR-0011: Statute corpus acquisition, versioning and section keys

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name) |
| Related | FR-12 to FR-17; R-01, R-10; architecture §7.2, §7.8, §11.2, §11.4; ADR-0001, ADR-0004; P3 |

## Context

Citations can only be verified against text that was ingested faithfully from official sources
(R-01, R-10). P3 adds the acquisition pipeline. AGENTS.md §9 requires an ADR when a change adds a
runtime dependency (jsoup, PDFBox) or changes what data is stored. Reconnaissance of the official
sources on 2026-11-11 shaped the decisions below:

- bdlaws serves `/act-print-{id}.html`, the whole act in one UTF-8 page (about 1.3 MB for the
  Labour Act), and `/act-{id}.html`, an index of every section with its number and heading. The
  two list the same sections in the same order. Many other pages are served as UTF-16BE with a
  byte-order mark, so the charset header cannot be trusted.
- `robots.txt` on bdlaws answers `200` with an HTML "404" page. That is not a robots policy.
- Amendments appear as footnotes on the amended passage ("substituted", "inserted", "omitted")
  with a link to the amending act. The portal shows only the current text; it does not publish
  the text a section had before an amendment.
- Section numbers carry Bangla letter suffixes (`৩ক`, `১৯৬খ`), use two different full stops
  (U+0964 and U+09F7), and ranges for repealed groups (`76-123`). Some rows in the English
  Contract Act have no number: they are further headed paragraphs of the previous section.
- NORMLEX answers automated clients with a Cloudflare challenge (HTTP 403, including
  `robots.txt`). The project does not circumvent bot detection.

## Decision

1. **Dependencies.** jsoup 1.24.1 (HTML parsing and the polite HTTP client), Apache PDFBox 3.0.8
   (fallback when the HTML has no section bodies) and jqwik 1.9.3 (tests only), each at the
   latest release on Maven Central on 2026-11-11. PDFBox arrives in P3 (earlier than ADR-0001's
   note) because FR-12 requires the fallback.
2. **Fetching.** One request per second at most, `robots.txt` honoured (a non-text response is
   treated as "no policy"), User-Agent with a contact from `SCRAPER_CONTACT`, at most 2 retries
   on 408, 429, 5xx and connection errors with backoff from 500 ms. Pages are decoded by byte-order
   mark first. Raw bytes are cached under `data/raw/` with a SHA-256 sidecar.
3. **Source of sections.** The act index gives number, heading and chapter; the print page gives
   the text. Counts must match, or the act is recorded as failed. A row without a number is
   appended to the previous numbered section, never given an invented number.
4. **Versions.** The current text of a section is the version labelled by its latest amending act
   (`amend-{year}`, `original` if none). An older version row exists only when this corpus
   ingested that text earlier or the source publishes it; it is never reconstructed. A changed
   text under the same label gets `{label}-r{n}`. The previous row becomes non-current and an
   `AMENDED_BY` edge links old to new. Sections the source omits or repeals are stored with
   `is_current = false`, so the verifier never accepts them (R-01). Sections that vanish from
   the source become non-current and are listed in the quality report.
5. **Section keys.** `{actCode}:{sectionNo}@{versionLabel}` with Latin digits. Bangla suffix
   letters map to Latin letters in alphabetical order of the Bangla consonants (`ক`→`A`, `খ`→`B`,
   `গ`→`C` …). This is a key-encoding convention, not legal content: the original form stays in
   `section_no_original`, and cross-references use the same mapping.
6. **Extra column.** `law_section.amendment_notes jsonb` keeps the footnote text and kind the
   parser read, so the lineage is auditable. Architecture §11.2 is updated in the same commit.
7. **ILO texts.** NORMLEX files are saved by a person under `data/raw/ilo/` and read by the same
   ingestor; unreviewed pack entries are not ingested. Ratification status is copied from the
   NORMLEX page, never assumed.
8. **ILO mapping.** `data/mappings/bd-ilo.yaml` rows start `reviewed: false`. The loader flags
   unreviewed rows and rows reviewed by their author, never reports them as verified, and, once a
   corpus release exists, refuses to start when a reviewed row's `ilo_quote` is not in the corpus.
9. **Locking.** One ingestion at a time per database (PostgreSQL advisory lock); a second request
   gets `409 ingest-running`.

## Consequences

Positive: the corpus is small to fetch (a handful of requests per run), reproducible (hashes,
release SHA-256) and honest about gaps (missing sections are reported, never filled).

Negative: old versions accumulate only from our own history, so "as of date" analysis before the
first ingestion is not possible. The Bangla-suffix key mapping is a convention to be explained
in the UI. NORMLEX needs a manual download step.

## Alternatives

- Per-section pages: about 370 requests per act instead of 2. Rejected on politeness.
- Take amendment text from model memory to reconstruct old versions: forbidden by R-10.
- Bypass the Cloudflare challenge: forbidden by the project's rules on bot detection.
- Generated `section_key`: impossible, it must read `law_act.code` (architecture §11.2).

## Verification

`ScraperPolicyTest`, `SectionParserGoldenTest`, `AmendmentParserTest`, `CrossReferenceExtractorTest`,
`IngestionIdempotencyIT`, `QualityReportTest`, `IloMappingLoaderTest`; the quality report in
`eval/reports/corpus-quality.md`.
