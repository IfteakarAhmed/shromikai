package bd.shromikai.retrieval;

import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.text.BanglaNormalizer;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Fictional acts and sections for retrieval tests (pack xx, R-10). Nothing here is law. */
final class RetrievalFixtures {

  static final String ACT_LAB = "XR-LAB";
  static final String ACT_DEF = "XR-DEF";
  static final String ACT_ILO = "XR-ILO";

  private RetrievalFixtures() {}

  static void clean(JdbcClient jdbc) {
    jdbc.sql("delete from law_act where code like 'XR-%'").update();
  }

  static long act(JdbcClient jdbc, String code, String jurisdiction) {
    return act(jdbc, code, jurisdiction, "en");
  }

  static long act(JdbcClient jdbc, String code, String jurisdiction, String lang) {
    return jdbc.sql(
            """
            insert into law_act (code, title_en, jurisdiction, source_url, authoritative_lang)
            values (:code, :title, :jurisdiction, 'https://example.test/xr', :lang)
            returning id
            """)
        .param("code", code)
        .param("title", "Fictional act " + code)
        .param("jurisdiction", jurisdiction)
        .param("lang", lang)
        .query(Long.class)
        .single();
  }

  static long section(
      JdbcClient jdbc,
      long actId,
      String actCode,
      String no,
      String version,
      boolean current,
      String heading,
      String text) {
    return jdbc.sql(
            """
            insert into law_section (act_id, section_no, heading_en, text_en, version_label,
                                     is_current, source_url, content_hash, normalised_text,
                                     section_key)
            values (:act, :no, :heading, :text, :version, :current,
                    'https://example.test/xr/' || :no, :hash, :normalised, :key)
            returning id
            """)
        .param("act", actId)
        .param("no", no)
        .param("heading", heading)
        .param("text", text)
        .param("version", version)
        .param("current", current)
        .param("hash", Hashing.sha256Hex(heading + "\n" + text))
        .param("normalised", BanglaNormalizer.matchingForm(heading + " " + text))
        .param("key", actCode + ":" + no + "@" + version)
        .query(Long.class)
        .single();
  }

  static void edge(JdbcClient jdbc, long from, long to, String kind) {
    jdbc.sql("insert into section_edge (from_section_id, to_section_id, kind) values (:f, :t, :k)")
        .param("f", from)
        .param("t", to)
        .param("k", kind)
        .update();
  }

  /** The text the backfill embeds for a fixture section. */
  static String embeddingInput(String heading, String text) {
    return EmbeddingBackfillService.input(null, heading, text);
  }
}
