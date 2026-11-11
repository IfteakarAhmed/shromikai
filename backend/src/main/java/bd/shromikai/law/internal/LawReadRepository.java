package bd.shromikai.law.internal;

import bd.shromikai.law.ActSummary;
import bd.shromikai.law.Section;
import bd.shromikai.law.SectionRef;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read-only SQL of the law API (FR-22). */
@Repository
public class LawReadRepository {

  private final JdbcClient jdbc;

  LawReadRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<ActSummary> acts(List<String> jurisdictions) {
    return jdbc.sql(ACT_SQL + " where a.jurisdiction in (:jurisdictions) order by a.code")
        .param("jurisdictions", jurisdictions)
        .query(LawReadRepository::actSummary)
        .list();
  }

  public Optional<ActSummary> act(String code) {
    return jdbc.sql(ACT_SQL + " where a.code = :code")
        .param("code", code)
        .query(LawReadRepository::actSummary)
        .optional();
  }

  /** Current sections of an act in numeric then lexical order of the section number. */
  public List<SectionRef> currentSections(String code) {
    return jdbc.sql(
            """
            select s.section_key, s.section_no, s.section_no_original, s.heading_bn, s.heading_en
              from law_section s join law_act a on a.id = s.act_id
             where a.code = :code and s.is_current
             order by nullif(regexp_replace(s.section_no, '[^0-9].*$', ''), '')::int nulls last,
                      s.section_no
            """)
        .param("code", code)
        .query(
            (rs, row) ->
                new SectionRef(
                    rs.getString("section_key"),
                    rs.getString("section_no"),
                    rs.getString("section_no_original"),
                    rs.getString("heading_bn"),
                    rs.getString("heading_en")))
        .list();
  }

  /** One section version; the current one when {@code version} is {@code null}. */
  public Optional<Section> section(
      String code, String sectionNo, @Nullable String version, @Nullable String corpusRelease) {
    return jdbc.sql(
            """
            select s.id, s.section_key, a.code, a.title_bn, a.title_en, s.section_no,
                   s.section_no_original, s.heading_bn, s.heading_en, a.authoritative_lang,
                   s.text_bn, s.text_en, s.version_label, s.effective_from, s.is_current,
                   s.source_url, s.fetched_at, s.content_hash
              from law_section s join law_act a on a.id = s.act_id
             where a.code = :code and s.section_no = :no
               and (cast(:version as text) is null and s.is_current
                    or s.version_label = cast(:version as text))
             order by s.is_current desc, s.id desc
             limit 1
            """)
        .param("code", code)
        .param("no", sectionNo)
        .param("version", version)
        .query(
            (rs, row) -> {
              boolean bn = "bn".equals(rs.getString("authoritative_lang"));
              String own = bn ? rs.getString("text_bn") : rs.getString("text_en");
              String other = bn ? rs.getString("text_en") : rs.getString("text_bn");
              long id = rs.getLong("id");
              return new Section(
                  rs.getString("section_key"),
                  rs.getString("code"),
                  rs.getString("title_bn"),
                  rs.getString("title_en"),
                  rs.getString("section_no"),
                  rs.getString("section_no_original"),
                  rs.getString("heading_bn"),
                  rs.getString("heading_en"),
                  rs.getString("authoritative_lang"),
                  own == null ? "" : own,
                  other == null || other.isBlank()
                      ? null
                      : new Section.Translation(bn ? "en" : "bn", other, false),
                  rs.getString("version_label"),
                  rs.getDate("effective_from") == null
                      ? null
                      : rs.getDate("effective_from").toLocalDate(),
                  rs.getBoolean("is_current"),
                  rs.getString("source_url"),
                  rs.getTimestamp("fetched_at").toInstant(),
                  rs.getString("content_hash"),
                  related(id),
                  corpusRelease);
            })
        .optional();
  }

  private List<Section.Related> related(long sectionId) {
    return jdbc.sql(
            """
            select t.section_key, e.kind, e.reviewed
              from section_edge e join law_section t on t.id = e.to_section_id
             where e.from_section_id = :id and t.is_current
             order by e.kind, t.section_key
            """)
        .param("id", sectionId)
        .query(
            (rs, row) ->
                new Section.Related(
                    rs.getString("section_key"), rs.getString("kind"), rs.getBoolean("reviewed")))
        .list();
  }

  private static final String ACT_SQL =
      """
      select a.code, a.title_bn, a.title_en, a.year, a.jurisdiction, a.authoritative_lang,
             a.status, a.ratification_status, a.source_url,
             (select count(*) from law_section s where s.act_id = a.id and s.is_current) as sections
        from law_act a
      """;

  private static ActSummary actSummary(java.sql.ResultSet rs, int row)
      throws java.sql.SQLException {
    int year = rs.getInt("year");
    Integer yearOrNull = rs.wasNull() ? null : year;
    return new ActSummary(
        rs.getString("code"),
        rs.getString("title_bn"),
        rs.getString("title_en"),
        yearOrNull,
        rs.getString("jurisdiction"),
        rs.getString("authoritative_lang"),
        rs.getString("status"),
        rs.getString("ratification_status"),
        rs.getString("source_url"),
        rs.getLong("sections"));
  }
}
