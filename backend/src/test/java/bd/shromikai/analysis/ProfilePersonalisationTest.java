package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.memo.PlainLanguageLint;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-56: the profile changes wording only; findings are identical across reading levels. */
@Tag("unit")
@Requirement("FR-56")
class ProfilePersonalisationTest {

  private static AnalysisRequest at(String level) {
    return new AnalysisRequest(
        Side.WORKER, "en", Map.of("reading_level", level, "sector", "ready-made garments"));
  }

  private static List<String> shape(AnalysisJob job) {
    return job.outcome().findings().stream()
        .map(f -> f.id() + "|" + f.severity() + "|" + f.verification() + "|" + f.clauseIds())
        .toList();
  }

  @Test
  void findingsAreIdenticalAcrossReadingLevelsAndTheBasicMemoHasShorterSentences() {
    AnalysisJob basic = PipelineHarness.standard().run(at("BASIC"));
    AnalysisJob standard = PipelineHarness.standard().run(at("STANDARD"));

    assertThat(basic.status()).isEqualTo(AnalysisJob.Status.ANALYSED);
    assertThat(shape(basic)).isEqualTo(shape(standard));
    double basicAverage =
        PlainLanguageLint.averageSentenceWords(basic.outcome().narrative().summary().reveal());
    double standardAverage =
        PlainLanguageLint.averageSentenceWords(standard.outcome().narrative().summary().reveal());
    assertThat(basicAverage).isLessThan(standardAverage);
  }
}
