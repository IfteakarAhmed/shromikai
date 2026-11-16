package bd.shromikai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * FR-36, R-10: the shipped rule file keeps its review trail. Every active rule names a reviewer who
 * is not its author, says the reviewer is not a lawyer, and carries a verbatim quote. The quote is
 * compared with the statute text by {@code ./gradlew checkRules}, which needs the ingested corpus
 * and so is not part of CI (run it locally, or {@code ./gradlew check -PcheckRules}).
 */
@Tag("unit")
@Requirement("FR-36")
class ShippedRulesFileTest {

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rules() throws IOException {
    String text = Files.readString(Path.of("../data/rules/bd-labour.yaml"));
    return (List<Map<String, Object>>)
        new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
  }

  @Test
  void everyActiveRuleHasAReviewerWhoIsNotTheAuthorAndAQuote() throws IOException {
    List<Map<String, Object>> rules = rules();

    assertThat(rules).isNotEmpty();
    for (Map<String, Object> rule : rules) {
      String id = String.valueOf(rule.get("id"));
      if ("active".equals(rule.get("status"))) {
        assertThat(rule.get("reviewed_by")).as(id + " reviewed_by").isNotNull();
        assertThat(rule.get("reviewed_by"))
            .as(id + " reviewer is not the author")
            .isNotEqualTo(rule.get("authored_by"));
        assertThat(String.valueOf(rule.get("review_note"))).as(id).contains("not a lawyer");
        @SuppressWarnings("unchecked")
        Map<String, Object> basis = (Map<String, Object>) rule.get("legal_basis");
        assertThat(String.valueOf(basis.get("quote")).length()).as(id + " quote").isGreaterThan(20);
      }
    }
  }
}
