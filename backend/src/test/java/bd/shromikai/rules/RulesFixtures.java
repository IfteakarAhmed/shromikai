package bd.shromikai.rules;

import bd.shromikai.rules.RuleLoader.CorpusLookup;
import bd.shromikai.rules.RuleLoader.CurrentSection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The fictional pack xx (R-10): acts, sections and rules that are not law. */
public final class RulesFixtures {

  public static final Path XX_RULES = Path.of("src/test/resources/packs/xx/xx-rules.yaml");

  private RulesFixtures() {}

  /** A tiny corpus keyed by act and section number. */
  public static CorpusLookup corpus() {
    Map<String, CurrentSection> sections = new HashMap<>();
    put(
        sections,
        "9",
        "An employer shall give a worker written notice of dismissal at least fourteen days before"
            + " the dismissal takes effect.");
    put(sections, "11", "Ordinary work shall not exceed forty hours in any week.");
    put(sections, "13", "Every worker is entitled to paid annual leave after one year of service.");
    put(
        sections,
        "15",
        "A worker shall not be compelled to work overtime against the worker's will.");
    put(sections, "20", "Wages shall be paid in money.");
    return (act, section) -> Optional.ofNullable(sections.get(act + ":" + section));
  }

  private static void put(Map<String, CurrentSection> sections, String no, String text) {
    sections.put("XX-LAB:" + no, new CurrentSection("XX-LAB:" + no + "@original", text));
  }

  public static Path write(Path dir, String name, String yaml) {
    try {
      return Files.writeString(dir.resolve(name), yaml);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Rule rule(
      String id,
      FactParameter fact,
      RuleComparator comparator,
      Double value,
      Unit unit,
      Rule.Status status) {
    return new Rule(
        id,
        "OTHER",
        fact,
        comparator,
        value,
        unit,
        null,
        new Rule.LegalBasis("XX-LAB", "1", "a fictional quote of at least twenty characters"),
        Severity.HIGH,
        "কাল্পনিক ব্যাখ্যা",
        "Fictional explanation",
        "author@example.test",
        status == Rule.Status.ACTIVE ? "reviewer@example.test" : null,
        null,
        status,
        "XX-LAB:1@original");
  }
}
