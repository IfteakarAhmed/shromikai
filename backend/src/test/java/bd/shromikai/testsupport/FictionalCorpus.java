package bd.shromikai.testsupport;

import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.text.BanglaNormalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A fictional act in the test database (pack xx, R-10). Every text says it is fictional; none of it
 * is law and none of it is used as a rule source. The act is registered as jurisdiction {@code BD}
 * so the pipeline's pack accepts it.
 */
public final class FictionalCorpus {

  public static final String ACT = "XV-LAB";

  /** Section number to text. */
  public static final Map<String, String> SECTIONS = new LinkedHashMap<>();

  static {
    SECTIONS.put(
        "1",
        "Fictional section on probation. A probation period shall not exceed six months. During"
            + " probation a worker may be dismissed only with written notice of seven days.");
    SECTIONS.put(
        "2",
        "Fictional section on wages. Wages shall be paid within seven days after the end of the"
            + " wage period. An employer shall not deduct from wages more than ten percent of the"
            + " wages for any loss.");
    SECTIONS.put(
        "3",
        "Fictional section on hours. Ordinary work shall not exceed eight hours a day or"
            + " forty-eight hours a week.");
    SECTIONS.put(
        "4",
        "Fictional section on overtime. Overtime shall be paid at twice the ordinary rate of"
            + " wages.");
    SECTIONS.put(
        "5",
        "Fictional section on notice. An employer shall give a worker written notice of dismissal"
            + " at least fourteen days before the dismissal takes effect.");
    SECTIONS.put(
        "6",
        "Fictional section on competition. An agreement that restrains a worker from taking up"
            + " lawful employment after the end of service is void.");
    SECTIONS.put(
        "7",
        "Fictional section on maternity. A woman worker is entitled to sixteen weeks of paid"
            + " maternity leave.");
    SECTIONS.put(
        "8",
        "Fictional section on leave. A worker is entitled to ten days of paid casual leave in each"
            + " year.");
    SECTIONS.put(
        "9",
        "Fictional section on disputes. A worker may take a dispute to the Labour Court of the"
            + " fictional country.");
  }

  private FictionalCorpus() {}

  public static String key(String number) {
    return ACT + ":" + number + "@original";
  }

  public static void clean(JdbcClient jdbc) {
    jdbc.sql("delete from law_act where code = :code").param("code", ACT).update();
  }

  public static void load(JdbcClient jdbc) {
    clean(jdbc);
    long actId =
        jdbc.sql(
                """
                insert into law_act (code, title_en, jurisdiction, source_url, authoritative_lang)
                values (:code, 'Fictional Labour Act (tests only)', 'BD', 'https://example.test/xv', 'en')
                returning id
                """)
            .param("code", ACT)
            .query(Long.class)
            .single();
    SECTIONS.forEach(
        (no, text) ->
            jdbc.sql(
                    """
                    insert into law_section (act_id, section_no, heading_en, text_en, version_label,
                                             is_current, source_url, content_hash, normalised_text,
                                             section_key)
                    values (:act, :no, :heading, :text, 'original', true,
                            'https://example.test/xv/' || :no, :hash, :normalised, :key)
                    """)
                .param("act", actId)
                .param("no", no)
                .param("heading", "Fictional section " + no)
                .param("text", text)
                .param("hash", Hashing.sha256Hex(text))
                .param(
                    "normalised",
                    BanglaNormalizer.matchingForm("Fictional section " + no + " " + text))
                .param("key", key(no))
                .update());
  }
}
