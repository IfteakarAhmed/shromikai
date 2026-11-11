package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * FR-40: the same contract analysed as {@code WORKER} and as {@code EMPLOYER} gives the same rule
 * findings and the same verified section keys for conflicts; only the framing differs. The model
 * answers are scripted stand-ins whose wording names the side (so the framing really differs); the
 * test proves the pipeline does not let the side change what is found or verified.
 */
@Requirement("FR-40")
class SideSymmetryIT extends DevPipelineIT {

  private record Summary(
      TreeSet<String> ruleFindings, TreeSet<String> conflictKeys, TreeSet<String> allStatuses) {}

  private Summary summary(JsonNode result) {
    TreeSet<String> rules = new TreeSet<>();
    TreeSet<String> conflictKeys = new TreeSet<>();
    TreeSet<String> statuses = new TreeSet<>();
    for (JsonNode f : result.get("findings")) {
      if (f.get("source").asText().equals("RULE_ENGINE")) {
        rules.add(f.get("clauseIds") + "/" + f.get("ruleId").asText());
      }
      if (f.get("kind").asText().equals("CONFLICT")
          && f.get("verificationStatus").asText().equals("VERIFIED")) {
        f.get("citations").forEach(c -> conflictKeys.add(c.get("sectionKey").asText()));
      }
      statuses.add(f.get("clauseType").asText() + ":" + f.get("verificationStatus").asText());
    }
    return new Summary(rules, conflictKeys, statuses);
  }

  @Test
  void shouldFindAndVerifyTheSameThingsForBothSides() {
    JsonNode worker = findings(run("syn-en-02", "WORKER"));
    JsonNode employer = findings(run("syn-en-02", "EMPLOYER"));

    Summary w = summary(worker);
    Summary e = summary(employer);

    assertThat(w.conflictKeys()).isNotEmpty();
    assertThat(e.conflictKeys()).isEqualTo(w.conflictKeys());
    assertThat(e.ruleFindings()).isEqualTo(w.ruleFindings());
    assertThat(e.allStatuses()).isEqualTo(w.allStatuses());
    List<String> workerText = new ArrayList<>();
    worker.get("findings").forEach(f -> workerText.add(f.get("explanation").asText()));
    List<String> employerText = new ArrayList<>();
    employer.get("findings").forEach(f -> employerText.add(f.get("explanation").asText()));
    assertThat(workerText).anyMatch(t -> t.contains("WORKER"));
    assertThat(employerText).anyMatch(t -> t.contains("EMPLOYER"));
    assertThat(worker.get("findings")).hasSameSizeAs(employer.get("findings"));
  }
}
