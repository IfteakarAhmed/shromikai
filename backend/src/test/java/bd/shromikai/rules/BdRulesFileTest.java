package bd.shromikai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The shipped {@code data/rules/bd-labour.yaml} is well formed (R-10). It does not check quotes
 * against the corpus here: that happens at application startup and in the draft report, and it
 * needs a corpus release.
 */
@Tag("unit")
@Requirement("FR-36")
class BdRulesFileTest {

  @Test
  void shouldBeValidAgainstTheSchemaAndNeverActiveWithoutAnotherReviewer() {
    RuleLoader.Result result =
        RuleLoader.load(
            Path.of("../data/rules/bd-labour.yaml"), (act, section) -> Optional.empty(), false);

    assertThat(result.rules()).hasSizeGreaterThanOrEqualTo(8);
    assertThat(result.rules())
        .allSatisfy(
            rule -> {
              assertThat(rule.legalBasis().act()).isEqualTo("BLA-2006");
              assertThat(rule.authoredBy()).isNotBlank();
              if (rule.active()) {
                assertThat(rule.twoPersonReviewed()).isTrue();
              }
            });
  }
}
