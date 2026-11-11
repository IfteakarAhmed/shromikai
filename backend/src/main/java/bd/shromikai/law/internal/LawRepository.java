package bd.shromikai.law.internal;

import bd.shromikai.law.internal.VersionPlanner.Existing;
import bd.shromikai.shared.Hashing;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** All SQL of statute ingestion (module {@code law}); JdbcClient only, no JPA (AGENTS.md §6). */
@Component
final class LawRepository {

  record ActRow(
      String code,
      String titleEn,
      @Nullable String titleBn,
      @Nullable Integer year,
      String jurisdiction,
      String sourceUrl,
      Instant fetchedAt,
      String authoritativeLang,
      String status,
      @Nullable String sourceSha256,
      @Nullable String ratificationStatus) {}

  record SectionWrite(
      String sectionNo,
      String sectionNoOriginal,
      String heading,
      String text,
      String lang,
      String versionLabel,
      @Nullable LocalDate effectiveFrom,
      boolean current,
      String sourceUrl,
      String contentHash,
      String parserVersion,
      String chapter,
      Instant fetchedAt,
      String normalisedText,
      String sectionKey,
      String notesJson) {}

  record ReleaseRow(
      long id, String label, String sha256, int actCount, int sectionCount, Instant createdAt) {}

  private final JdbcClient jdbc;
  private final Clock clock;

  LawRepository(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  long upsertAct(ActRow a) {
    return jdbc.sql(
            """
            insert into law_act (code, title_en, title_bn, year, jurisdiction, source_url, fetched_at,
                                 authoritative_lang, status, source_sha256, ratification_status)
            values (:code, :titleEn, :titleBn, :year, :jurisdiction, :sourceUrl, :fetchedAt,
                    :lang, :status, :sha, :ratification)
            on conflict (code) do update set
                title_en = excluded.title_en, title_bn = excluded.title_bn, year = excluded.year,
                jurisdiction = excluded.jurisdiction, source_url = excluded.source_url,
                fetched_at = excluded.fetched_at, authoritative_lang = excluded.authoritative_lang,
                status = excluded.status, source_sha256 = excluded.source_sha256,
                ratification_status = excluded.ratification_status
            returning id
            """)
        .param("code", a.code())
        .param("titleEn", a.titleEn())
        .param("titleBn", a.titleBn())
        .param("year", a.year())
        .param("jurisdiction", a.jurisdiction())
        .param("sourceUrl", a.sourceUrl())
        .param("fetchedAt", Timestamp.from(a.fetchedAt()))
        .param("lang", a.authoritativeLang())
        .param("status", a.status())
        .param("sha", a.sourceSha256())
        .param("ratification", a.ratificationStatus())
        .query(Long.class)
        .single();
  }

  /** Stored versions of an act, by Latin section number. */
  Map<String, List<Existing>> existingVersions(long actId) {
    Map<String, List<Existing>> out = new LinkedHashMap<>();
    jdbc.sql(
            "select id, section_no, version_label, coalesce(content_hash, '') as hash, is_current from law_section where act_id = :act")
        .param("act", actId)
        .query(
            (rs, row) -> {
              out.computeIfAbsent(rs.getString("section_no"), k -> new ArrayList<>())
                  .add(
                      new Existing(
                          rs.getLong("id"),
                          rs.getString("version_label"),
                          rs.getString("hash"),
                          rs.getBoolean("is_current")));
              return null;
            })
        .list();
    return out;
  }

  long insertSection(long actId, SectionWrite w) {
    boolean bn = w.lang().equals("bn");
    return jdbc.sql(
            """
            insert into law_section (act_id, section_no, heading_en, heading_bn, text_en, text_bn,
                                     version_label, effective_from, is_current, source_url,
                                     content_hash, parser_version, section_no_original, chapter,
                                     fetched_at, normalised_text, section_key, amendment_notes)
            values (:act, :no, :headingEn, :headingBn, :textEn, :textBn, :label, :effective, :current,
                    :url, :hash, :parser, :original, :chapter, :fetchedAt, :normalised, :key,
                    cast(:notes as jsonb))
            returning id
            """)
        .param("act", actId)
        .param("no", w.sectionNo())
        .param("headingEn", bn ? null : w.heading())
        .param("headingBn", bn ? w.heading() : null)
        .param("textEn", bn ? null : w.text())
        .param("textBn", bn ? w.text() : null)
        .param("label", w.versionLabel())
        .param(
            "effective",
            w.effectiveFrom() == null ? null : java.sql.Date.valueOf(w.effectiveFrom()))
        .param("current", w.current())
        .param("url", w.sourceUrl())
        .param("hash", w.contentHash())
        .param("parser", w.parserVersion())
        .param("original", w.sectionNoOriginal())
        .param("chapter", w.chapter())
        .param("fetchedAt", Timestamp.from(w.fetchedAt()))
        .param("normalised", w.normalisedText())
        .param("key", w.sectionKey())
        .param("notes", w.notesJson())
        .query(Long.class)
        .single();
  }

  void demote(Collection<Long> ids) {
    for (long id : ids) {
      jdbc.sql("update law_section set is_current = false where id = :id").param("id", id).update();
    }
  }

  void setCurrent(long id, boolean current) {
    jdbc.sql("update law_section set is_current = :c where id = :id")
        .param("c", current)
        .param("id", id)
        .update();
  }

  /** Current sections of the act that the source no longer lists become non-current. */
  List<String> withdrawMissing(long actId, Set<String> presentSectionNos) {
    List<String> withdrawn = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    jdbc.sql("select id, section_no from law_section where act_id = :act and is_current")
        .param("act", actId)
        .query(
            (rs, row) -> {
              if (!presentSectionNos.contains(rs.getString("section_no"))) {
                demote(List.of(rs.getLong("id")));
                if (seen.add(rs.getString("section_no"))) {
                  withdrawn.add(rs.getString("section_no"));
                }
              }
              return null;
            })
        .list();
    return withdrawn;
  }

  void touchCurrent(long actId, Instant at) {
    jdbc.sql("update law_section set fetched_at = :at where act_id = :act and is_current")
        .param("at", Timestamp.from(at))
        .param("act", actId)
        .update();
  }

  /** Latin section number to id, for current sections of one act. */
  Map<String, Long> currentIds(long actId) {
    Map<String, Long> out = new LinkedHashMap<>();
    jdbc.sql("select section_no, id from law_section where act_id = :act and is_current")
        .param("act", actId)
        .query((rs, row) -> out.put(rs.getString("section_no"), rs.getLong("id")))
        .list();
    return out;
  }

  void clearDerivedEdges(long actId) {
    jdbc.sql(
            """
            delete from section_edge
             where kind in ('REFERS_TO', 'USES_DEFINITION')
               and from_section_id in (select id from law_section where act_id = :act)
            """)
        .param("act", actId)
        .update();
  }

  void clearMappingEdges() {
    jdbc.sql("delete from section_edge where kind = 'ALIGNS_WITH' and source = 'MAPPING'").update();
  }

  void insertEdge(
      long from,
      long to,
      String kind,
      String source,
      boolean reviewed,
      @Nullable String reviewedBy) {
    jdbc.sql(
            """
            insert into section_edge (from_section_id, to_section_id, kind, source, reviewed, reviewed_by)
            values (:from, :to, :kind, :source, :reviewed, :by)
            on conflict (from_section_id, to_section_id, kind) do nothing
            """)
        .param("from", from)
        .param("to", to)
        .param("kind", kind)
        .param("source", source)
        .param("reviewed", reviewed)
        .param("by", reviewedBy)
        .update();
  }

  void clearTerms(long actId) {
    jdbc.sql(
            "delete from law_term where kind = 'DEFINED_TERM' and section_id in (select id from law_section where act_id = :act)")
        .param("act", actId)
        .update();
  }

  void insertTerm(long sectionId, String code, @Nullable String termBn, @Nullable String termEn) {
    jdbc.sql(
            """
            insert into law_term (section_id, kind, code, term_bn, term_en)
            values (:section, 'DEFINED_TERM', :code, :bn, :en)
            on conflict (kind, code) do nothing
            """)
        .param("section", sectionId)
        .param("code", code)
        .param("bn", termBn)
        .param("en", termEn)
        .update();
  }

  /** Authoritative text of a current section, by section key. */
  java.util.Optional<String> authoritativeText(String sectionKey) {
    return jdbc.sql(
            """
            select case when a.authoritative_lang = 'bn' then s.text_bn else s.text_en end
              from law_section s join law_act a on a.id = s.act_id
             where s.section_key = :key and s.is_current
            """)
        .param("key", sectionKey)
        .query(String.class)
        .optional();
  }

  java.util.Optional<Long> currentSectionId(String sectionKey) {
    return jdbc.sql("select id from law_section where section_key = :key and is_current")
        .param("key", sectionKey)
        .query(Long.class)
        .optional();
  }

  boolean hasRelease() {
    return jdbc.sql("select count(*) from corpus_release").query(Long.class).single() > 0;
  }

  /** New corpus release: label {@code YYYY.MM.DD-n}, SHA-256 over the current sections' hashes. */
  ReleaseRow createRelease() {
    Instant now = clock.instant();
    LocalDate day = now.atZone(ZoneOffset.UTC).toLocalDate();
    String prefix =
        "%04d.%02d.%02d-".formatted(day.getYear(), day.getMonthValue(), day.getDayOfMonth());
    long n =
        jdbc.sql("select count(*) from corpus_release where label like :p")
                .param("p", prefix + "%")
                .query(Long.class)
                .single()
            + 1;
    StringBuilder material = new StringBuilder();
    jdbc.sql(
            "select section_key, coalesce(content_hash, '') as h from law_section where is_current order by section_key")
        .query(
            (rs, row) ->
                material
                    .append(rs.getString("section_key"))
                    .append(':')
                    .append(rs.getString("h"))
                    .append('\n'))
        .list();
    String sha = Hashing.sha256Hex(material.toString());
    int acts =
        jdbc.sql("select count(distinct act_id) from law_section where is_current")
            .query(Integer.class)
            .single();
    int sections =
        jdbc.sql("select count(*) from law_section where is_current").query(Integer.class).single();
    String label = prefix + n;
    long id =
        jdbc.sql(
                """
            insert into corpus_release (label, sha256, act_count, section_count, created_at)
            values (:label, :sha, :acts, :sections, :at) returning id
            """)
            .param("label", label)
            .param("sha", sha)
            .param("acts", acts)
            .param("sections", sections)
            .param("at", Timestamp.from(now))
            .query(Long.class)
            .single();
    return new ReleaseRow(id, label, sha, acts, sections, now);
  }

  /** Every stored section version, for the quality report. */
  List<QualityReport.SectionFacts> sectionFacts() {
    return jdbc.sql(
            """
            select a.code, s.section_key, s.section_no, s.source_url, s.is_current,
                   coalesce(s.heading_bn, s.heading_en, '') as heading,
                   (s.text_bn is not null and btrim(s.text_bn) <> '') as has_bn,
                   (s.text_en is not null and btrim(s.text_en) <> '') as has_en,
                   (not s.is_current
                    and btrim(coalesce(s.text_bn, s.text_en, '')) ~* '^\\[?\\s*(\\*\\*\\*|বিলুপ্ত|রহিত|repealed|omitted)') as omitted
              from law_section s join law_act a on a.id = s.act_id
             order by a.code, s.section_key
            """)
        .query(
            (rs, row) ->
                new QualityReport.SectionFacts(
                    rs.getString("code"),
                    rs.getString("section_key"),
                    rs.getString("section_no"),
                    rs.getString("source_url"),
                    rs.getBoolean("has_bn"),
                    rs.getBoolean("has_en"),
                    rs.getBoolean("is_current"),
                    rs.getBoolean("omitted"),
                    rs.getString("heading")))
        .list();
  }

  Map<String, Long> edgeCounts() {
    Map<String, Long> out = new LinkedHashMap<>();
    for (String kind : List.of("REFERS_TO", "USES_DEFINITION", "AMENDED_BY", "ALIGNS_WITH")) {
      out.put(kind, 0L);
    }
    jdbc.sql("select kind, count(*) as n from section_edge group by kind")
        .query((rs, row) -> out.put(rs.getString("kind"), rs.getLong("n")))
        .list();
    return out;
  }
}
