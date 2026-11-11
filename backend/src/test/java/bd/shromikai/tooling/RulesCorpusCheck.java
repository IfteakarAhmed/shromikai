package bd.shromikai.tooling;

import bd.shromikai.rules.RuleLoader;
import java.nio.file.Path;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * {@code ./gradlew checkRules}: checks every rule of {@code data/rules/*.yaml}, draft or active,
 * against the current section text of the corpus in the local database, with the same quote match
 * the application uses at startup (FR-36). A draft rule whose quote is not in its section is
 * reported here before a reviewer is asked to read it. Exit code 1 if any rule has a problem.
 */
public final class RulesCorpusCheck {

  private RulesCorpusCheck() {}

  public static void main(String[] args) {
    JdbcClient jdbc =
        JdbcClient.create(
            new DriverManagerDataSource(
                env("DB_URL", "jdbc:postgresql://localhost:5432/shromikai"),
                env("DB_USER", "shromikai"),
                env("DB_PASSWORD", "shromikai")));
    RuleLoader.CorpusLookup corpus =
        (act, section) ->
            jdbc.sql(
                    """
                    select s.section_key, case when a.authoritative_lang = 'bn' then s.text_bn
                                               else s.text_en end as text
                      from law_section s join law_act a on a.id = s.act_id
                     where a.code = :act and s.section_no = :no and s.is_current
                    """)
                .param("act", act)
                .param("no", section)
                .query(
                    (rs, n) ->
                        new RuleLoader.CurrentSection(
                            rs.getString("section_key"),
                            rs.getString("text") == null ? "" : rs.getString("text")))
                .optional();
    int rules = 0;
    int active = 0;
    List<String> problems = new java.util.ArrayList<>();
    for (String file : args) {
      RuleLoader.Result result = RuleLoader.load(Path.of(file), corpus, true);
      rules += result.rules().size();
      active += result.activeRules().size();
      problems.addAll(result.draftProblems());
    }
    System.out.println(
        "rules checked: " + rules + " (" + active + " active, " + (rules - active) + " draft)");
    problems.forEach(p -> System.out.println("PROBLEM: " + p));
    if (!problems.isEmpty()) {
      System.exit(1);
    }
    System.out.println("every quote is in its current section");
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
