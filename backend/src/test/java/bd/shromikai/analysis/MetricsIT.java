package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FR-82: after one analysis (scripted stand-ins, fictional act) every metric of architecture §15
 * exists in the registry, and no label carries content: only enum-like values, model IDs, prompt
 * IDs and numbers.
 */
@Requirement("FR-82")
class MetricsIT extends DevPipelineIT {

  /** Label values a metric may carry: codes, model and prompt IDs, never free text. */
  private static final Pattern SAFE_LABEL = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

  @Autowired MeterRegistry meters;

  @Test
  void shouldRecordEveryMetricAfterOneAnalysis() {
    run("syn-en-02", "WORKER");

    assertThat(names())
        .contains(
            "shromikai_analysis_duration_seconds",
            "shromikai_analyses_total",
            "shromikai_llm_tokens_total",
            "shromikai_llm_cost_usd_total",
            "shromikai_llm_calls_total",
            "shromikai_verification_total",
            "shromikai_retrieval_cache_total",
            "shromikai_privacy_blocks_total",
            "shromikai_review_queue_open",
            "shromikai_jobstore_size");
    assertThat(meters.find("shromikai_analysis_duration_seconds").tag("stage", "RISK").timer())
        .isNotNull();
    assertThat(meters.find("shromikai_analyses_total").tag("status", "SUCCEEDED").counter().count())
        .isPositive();
    assertThat(meters.find("shromikai_verification_total").tag("status", "VERIFIED").counters())
        .isNotEmpty();
    assertThat(
            meters.find("shromikai_llm_calls_total").tag("prompt_id", "risk-analysis").counters())
        .isNotEmpty();
  }

  @Test
  void shouldCarryNoContentInAnyLabel() {
    run("syn-bn-01", "WORKER");

    Set<String> forbidden = Set.of("নমুনা", "Sample", "ZQXJ", "example.org");
    for (Meter meter : meters.getMeters()) {
      if (!meter.getId().getName().startsWith("shromikai_")) {
        continue;
      }
      List<Tag> tags = meter.getId().getTags();
      for (Tag tag : tags) {
        assertThat(tag.getValue())
            .as(meter.getId().getName() + " " + tag.getKey())
            .matches(SAFE_LABEL);
        forbidden.forEach(word -> assertThat(tag.getValue()).doesNotContain(word));
      }
    }
  }

  private Set<String> names() {
    return meters.getMeters().stream()
        .map(m -> m.getId().getName())
        .collect(java.util.stream.Collectors.toSet());
  }
}
