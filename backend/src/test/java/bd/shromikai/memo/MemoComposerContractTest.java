package bd.shromikai.memo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.memo.MemoInput.FindingView;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-51: the memo validates against its schema; chips refer only to verified citations. */
@Tag("unit")
@Requirement("FR-51")
class MemoComposerContractTest {

  private final MemoComposer composer = new MemoComposer(new ObjectMapper());
  private final JsonSchemas schemas = new JsonSchemas();

  private static FindingView finding(String id, String status, List<String> citations) {
    return new FindingView(
        id,
        "Title " + id,
        "HIGH",
        "POLICY_ONLY".equals(status) ? "PLAYBOOK" : "LLM_ANALYSIS",
        status,
        "An explanation of the risk in plain words.",
        "",
        citations,
        null);
  }

  private MemoInput input(String language, List<FindingView> findings) {
    return new MemoInput(
        UUID.randomUUID(),
        UUID.randomUUID(),
        language,
        "STANDARD",
        Instant.parse("2026-11-16T10:00:00Z"),
        "A short summary.",
        List.of("Talk to your employer.", "Keep a copy."),
        findings,
        List.of("r1"),
        List.of("o1", "o2"),
        new MemoInput.Verification(2, 1, 1, "SAME_FAMILY"),
        new MemoInput.Footer("2026.11.01-1", "p7-1", "k1"));
  }

  private List<FindingView> mixed() {
    return List.of(
        finding("f1", "VERIFIED", List.of("c-1", "c-2")),
        finding("f2", "UNCERTAIN", List.of()),
        finding("f3", "POLICY_ONLY", List.of()));
  }

  @Test
  void theMemoValidatesAgainstTheMemoSchemaInBothLanguages() {
    for (String language : List.of("bn", "en")) {
      ObjectNode memo = composer.compose(input(language, mixed()));

      assertThat(schemas.validate("schemas/api/memo.v1.schema.json", memo)).isEmpty();
      assertThat(memo.path("sections")).hasSize(8);
      assertThat(memo.path("disclaimer").asText()).isNotBlank();
    }
  }

  @Test
  void theBanglaMemoCarriesTheNotLegalAdviceDisclaimer() {
    ObjectNode memo = composer.compose(input("bn", mixed()));

    assertThat(memo.path("disclaimer").asText()).contains("এটা আইনি পরামর্শ নয়");
  }

  @Test
  void everyCitationChipRefersToACitationOfAVerifiedFinding() {
    ObjectNode memo = composer.compose(input("en", mixed()));

    assertThat(MemoComposer.chipIds(memo)).containsExactly("c-1", "c-2");
    assertThat(Set.copyOf(MemoComposer.chipIds(memo))).isSubsetOf("c-1", "c-2");
  }

  @Test
  void aFindingThatIsNotVerifiedCannotCarryACitation() {
    List<FindingView> bad = List.of(finding("f1", "UNCERTAIN", List.of("c-9")));

    assertThatThrownBy(() -> composer.compose(input("en", bad)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPolicyFindingIsLabelledAsPolicyAndNotAsLaw() {
    ObjectNode memo = composer.compose(input("en", mixed()));

    assertThat(memo.toString()).contains("Company policy (not law)");
  }

  @Test
  void anEmptyResultStillGivesAllEightSections() {
    ObjectNode memo = composer.compose(input("en", List.of()));

    assertThat(schemas.validate("schemas/api/memo.v1.schema.json", memo)).isEmpty();
  }
}
