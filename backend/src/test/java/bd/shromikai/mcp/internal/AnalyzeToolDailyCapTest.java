package bd.shromikai.mcp.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bd.shromikai.analysis.ExternalAnalysisService;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-66, NFR-06: one API key may start only a few analyses a day (the model cost is real); the cap
 * is per key, configurable under {@code shromikai.mcp.analyze-per-key-per-day}, and a refused call
 * reaches no model.
 */
@Tag("unit")
@Requirement("FR-66")
@Requirement("NFR-06")
class AnalyzeToolDailyCapTest {

  private final ExternalAnalysisService analyses = mock(ExternalAnalysisService.class);
  private final AnalyzeTool tool =
      new AnalyzeTool(analyses, new McpProperties(60, Duration.ofSeconds(5), 2));

  private Args args() {
    return new Args(Map.of("text", "x".repeat(300), "side", "WORKER", "output_language", "en"));
  }

  private void stubAnalysis() {
    when(analyses.analyse(any(), any(), any(), any(), any(), any()))
        .thenReturn(new ExternalAnalysisService.Result(new ObjectMapper().createObjectNode(), 100));
  }

  @Test
  void refusesTheThirdAnalysisOfTheDayWithQuotaExceededAndNeverCallsTheModel() {
    stubAnalysis();
    UUID key = UUID.randomUUID();
    UUID org = UUID.randomUUID();

    tool.analyse(args(), key, org);
    tool.analyse(args(), key, org);

    assertThatThrownBy(() -> tool.analyse(args(), key, org))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> {
              assertThat(e.type()).isEqualTo(ProblemType.QUOTA_EXCEEDED);
              assertThat(e.retryAfterSeconds()).isPositive();
            });
    verify(analyses, times(2)).analyse(any(), any(), any(), any(), any(), any());
  }

  @Test
  void countsKeysSeparately() {
    stubAnalysis();
    UUID org = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    tool.analyse(args(), first, org);
    tool.analyse(args(), first, org);

    assertThat(tool.analyse(args(), UUID.randomUUID(), org)).isNotNull();
  }

  @Test
  void anInvalidRequestDoesNotUseUpTheCap() {
    stubAnalysis();
    UUID key = UUID.randomUUID();
    UUID org = UUID.randomUUID();
    Args tooShort = new Args(Map.of("text", "short", "side", "WORKER"));

    assertThatThrownBy(() -> tool.analyse(tooShort, key, org)).isInstanceOf(ProblemException.class);
    assertThatThrownBy(() -> tool.analyse(tooShort, key, org)).isInstanceOf(ProblemException.class);

    assertThat(tool.analyse(args(), key, org)).isNotNull();
    assertThat(tool.analyse(args(), key, org)).isNotNull();
  }
}
