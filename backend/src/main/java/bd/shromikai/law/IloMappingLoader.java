package bd.shromikai.law;

import bd.shromikai.shared.text.QuoteMatcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads {@code data/mappings/bd-ilo.yaml}: {@code ALIGNS_WITH} rows between Bangladeshi sections
 * and ILO articles (FR-16, architecture §7.8). Every row starts {@code reviewed: false}. Only a row
 * that a person other than its author set to {@code reviewed: true}, whose ILO quote is found in
 * the corpus, is {@link Status#REVIEWED}; unreviewed rows and rows reviewed by their author are
 * flagged and never produce an ILO reference. A reviewed row whose quote is missing stops startup.
 */
public final class IloMappingLoader {

  /** Review state of one row. Only {@code REVIEWED} may back an ILO reference. */
  public enum Status {
    UNREVIEWED,
    SELF_REVIEWED,
    REVIEWED
  }

  /** One mapping row. */
  public record Row(
      String bdSectionKey,
      String iloSectionKey,
      String iloQuote,
      String claim,
      String authoredBy,
      boolean reviewedFlag,
      @Nullable String reviewedBy,
      Status status) {

    /** True only for a verified, two-person-reviewed row. */
    public boolean usable() {
      return status == Status.REVIEWED;
    }
  }

  /** Authoritative text of the current section with this key, if the corpus has it. */
  public interface CorpusLookup {
    Optional<String> authoritativeText(String sectionKey);
  }

  /** Minimum similarity of a reviewed row's ILO quote (architecture §7.7). */
  public static final double QUOTE_THRESHOLD = 0.92;

  private static final int QUOTE_MIN = 20;
  private static final int QUOTE_MAX = 300;

  private IloMappingLoader() {}

  /**
   * @param verifyQuotes check reviewed rows against the corpus; false only while no corpus release
   *     exists yet, so the first ingestion can run
   */
  public static List<Row> load(Path file, CorpusLookup corpus, boolean verifyQuotes) {
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    Object root;
    try (InputStream in = Files.newInputStream(file)) {
      root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (root == null) {
      return List.of();
    }
    if (!(root instanceof Map<?, ?> map)
        || !(map.get("rows") == null || map.get("rows") instanceof List<?>)) {
      throw new IloMappingException(file + ": expected a mapping with a 'rows' list");
    }
    List<Row> rows = new ArrayList<>();
    Object list = map.get("rows");
    if (list == null) {
      return List.of();
    }
    int index = 0;
    for (Object item : (List<?>) list) {
      index++;
      rows.add(row(file, index, item, corpus, verifyQuotes));
    }
    return rows;
  }

  private static Row row(
      Path file, int index, Object item, CorpusLookup corpus, boolean verifyQuotes) {
    String where = file.getFileName() + " row " + index;
    if (!(item instanceof Map<?, ?> m)) {
      throw new IloMappingException(where + ": row must be a mapping");
    }
    String bd = required(m, "bd_section_key", where);
    String ilo = required(m, "ilo_section_key", where);
    String quote = required(m, "ilo_quote", where);
    String claim = required(m, "claim", where);
    String author = required(m, "authored_by", where);
    Object flag = m.get("reviewed");
    if (flag != null && !(flag instanceof Boolean)) {
      throw new IloMappingException(where + ": 'reviewed' must be true or false");
    }
    boolean reviewed = Boolean.TRUE.equals(flag);
    String reviewer = m.get("reviewed_by") == null ? null : m.get("reviewed_by").toString().strip();
    SectionKey.parse(bd)
        .orElseThrow(() -> new IloMappingException(where + ": bad bd_section_key " + bd));
    SectionKey.parse(ilo)
        .orElseThrow(() -> new IloMappingException(where + ": bad ilo_section_key " + ilo));

    if (!reviewed) {
      return new Row(bd, ilo, quote, claim, author, false, reviewer, Status.UNREVIEWED);
    }
    if (reviewer == null || reviewer.isBlank() || reviewer.equalsIgnoreCase(author.strip())) {
      return new Row(bd, ilo, quote, claim, author, true, reviewer, Status.SELF_REVIEWED);
    }
    int length = QuoteMatcher.normalisedLength(quote);
    if (length < QUOTE_MIN || quote.length() > QUOTE_MAX) {
      throw new IloMappingException(
          where
              + ": reviewed ilo_quote must have "
              + QUOTE_MIN
              + " to "
              + QUOTE_MAX
              + " characters");
    }
    if (verifyQuotes) {
      String text =
          corpus
              .authoritativeText(ilo)
              .orElseThrow(
                  () ->
                      new IloMappingException(
                          where
                              + ": reviewed row, but "
                              + ilo
                              + " is not a current section of the corpus"));
      if (corpus.authoritativeText(bd).isEmpty()) {
        throw new IloMappingException(
            where + ": reviewed row, but " + bd + " is not a current section of the corpus");
      }
      if (QuoteMatcher.similarity(quote, text) < QUOTE_THRESHOLD) {
        throw new IloMappingException(where + ": reviewed ilo_quote is not found in " + ilo);
      }
    }
    return new Row(bd, ilo, quote, claim, author, true, reviewer, Status.REVIEWED);
  }

  private static String required(Map<?, ?> m, String key, String where) {
    Object value = m.get(key);
    if (value == null || value.toString().isBlank()) {
      throw new IloMappingException(where + ": missing '" + key + "'");
    }
    return value.toString().strip();
  }
}
