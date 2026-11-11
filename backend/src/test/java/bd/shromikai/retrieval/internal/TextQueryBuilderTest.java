package bd.shromikai.retrieval.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.retrieval.internal.SectionSearchRepository.DocFreq;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-19 / ADR-0013: the full-text query keeps discriminating words and picks a language. */
@Tag("unit")
@Requirement("FR-19")
class TextQueryBuilderTest {

  private static final DocFreq STATS =
      new DocFreq(Map.of("common", 300, "mid", 50, "rare", 2, "other", 3), 500);

  @Test
  void shouldDropWordsAboveTheDocumentFrequencyCutoff() {
    // cutoff = max(20, ceil(0.10 * 500)) = 50: "common" (300) goes, "mid" (50) stays
    assertThat(TextQueryBuilder.filter(List.of("common", "mid", "rare"), STATS, 0.10))
        .containsExactly("mid", "rare");
  }

  @Test
  void shouldKeepUnknownWords() {
    assertThat(TextQueryBuilder.filter(List.of("common", "never-seen"), STATS, 0.10))
        .containsExactly("never-seen");
  }

  @Test
  void shouldKeepTheRarestWordsWhenEveryWordWouldBeDropped() {
    DocFreq all = new DocFreq(Map.of("a", 400, "b", 300, "c", 350, "d", 450, "e", 500), 500);

    assertThat(TextQueryBuilder.filter(List.of("a", "b", "c", "d", "e"), all, 0.10))
        .containsExactly("b", "c", "a");
  }

  @Test
  void shouldNotFilterATinyCorpus() {
    DocFreq tiny = new DocFreq(Map.of("worker", 3, "zorblax", 1), 5);

    assertThat(TextQueryBuilder.filter(List.of("worker", "zorblax"), tiny, 0.10))
        .containsExactly("worker", "zorblax");
  }

  @Test
  void shouldDetectTheScriptOfTheQuery() {
    assertThat(TextQueryBuilder.language("কত ঘণ্টা")).isEqualTo("bn");
    assertThat(TextQueryBuilder.language("overtime pay")).isEqualTo("en");
    assertThat(TextQueryBuilder.language("overtime কাজ")).isEqualTo("bn");
    assertThat(TextQueryBuilder.language("12 34")).isNull();
  }
}
