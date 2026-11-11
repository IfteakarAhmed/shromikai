package bd.shromikai.privacy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("FR-30")
class AhoCorasickTest {

  @Test
  void shouldFindAnyPatternAndOverlappingOnes() {
    var ac = new AhoCorasick(List.of("he", "she", "his", "hers", "রহিম"));

    assertThat(ac.matches("ushers")).isTrue();
    assertThat(ac.matches("মোঃ রহিম উদ্দিন")).isTrue();
    assertThat(ac.matches("hi there")).isTrue();
    assertThat(ac.matches("xyz")).isFalse();
  }

  @Test
  void shouldMatchNothingWithoutPatterns() {
    assertThat(new AhoCorasick(List.of()).matches("anything")).isFalse();
    assertThat(new AhoCorasick(List.of("")).matches("anything")).isFalse();
  }

  @Test
  void shouldFindAPatternThatIsASuffixOfAnotherPartialMatch() {
    var ac = new AhoCorasick(List.of("abcd", "bce"));

    assertThat(ac.matches("abce")).isTrue();
  }
}
