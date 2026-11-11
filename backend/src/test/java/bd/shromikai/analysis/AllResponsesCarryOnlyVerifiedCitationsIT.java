package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.VerifiedOnlyScanner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * FR-47, R-01, M-01 = 0: every JSON response of the pipeline holds citations that are {@code
 * VERIFIED} and nothing else. There is no memo before P7, so the scan covers the findings, the
 * redacted view and the trace of the {@code dev} endpoints, over the synthetic contracts, including
 * one where the scripted model invents a quote.
 */
@Requirement("FR-47")
class AllResponsesCarryOnlyVerifiedCitationsIT extends DevPipelineIT {

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"syn-en-02", "syn-bn-01", "syn-en-01", "syn-bn-02", "syn-bn-03"})
  void shouldShowOnlyVerifiedCitationsInEveryResponse(String contract) {
    String id = run(contract, "WORKER");

    JsonNode findings = findings(id);
    JsonNode redacted = redacted(id);

    assertThat(VerifiedOnlyScanner.violations(findings)).isEmpty();
    assertThat(VerifiedOnlyScanner.violations(redacted)).isEmpty();
    assertThat(VerifiedOnlyScanner.citations(findings)).as("the scan saw citations").isPositive();
    // The invented quote of the scripted model never appears anywhere in a response.
    assertThat(findings.toString()).doesNotContain("every worker may keep any machine");
    assertThat(redacted.toString()).doesNotContain("every worker may keep any machine");
  }

  @Test
  void shouldNoticeAnUnverifiedCitationWhenOneExists() throws Exception {
    JsonNode leaking =
        new ObjectMapper()
            .readTree(
                "{\"findings\":[{\"citations\":[{\"sectionKey\":\"XV-LAB:1@original\",\"quote\":\"x\","
                    + "\"verification\":{\"status\":\"UNCERTAIN\"}},{\"sectionKey\":\"XV-LAB:2@original\","
                    + "\"quote\":\"y\"}]}]}");

    assertThat(VerifiedOnlyScanner.violations(leaking)).hasSize(2);
  }
}
