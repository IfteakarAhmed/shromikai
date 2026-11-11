package bd.shromikai.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.Arrays;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/** Properties of the 8-token rule (test-strategy.md §8, {@code OverlapChecker}). */
@Tag("unit")
@Requirement("FR-50")
class OverlapCheckerPropertyTest {

  @Provide
  Arbitrary<String> words() {
    return Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(8);
  }

  @Property(tries = 200)
  void shouldDetectAnyEightTokenWindowCopiedFromTheSource(
      @ForAll("sourceWords") java.util.List<String> sourceTokens, @ForAll int offset) {
    String source = String.join(" ", sourceTokens);
    int from = Math.floorMod(offset, sourceTokens.size() - OverlapChecker.WINDOW + 1);
    String copied = String.join(" ", sourceTokens.subList(from, from + OverlapChecker.WINDOW));

    assertThat(OverlapChecker.of(source).overlaps("prefix " + copied + " suffix")).isTrue();
  }

  @Provide
  Arbitrary<java.util.List<String>> sourceWords() {
    return words().list().ofMinSize(OverlapChecker.WINDOW).ofMaxSize(30);
  }

  @Property(tries = 100)
  void shouldNeverFlagSevenTokenSnippets(@ForAll("sourceWords") java.util.List<String> tokens) {
    String source = String.join(" ", tokens);
    String snippet = String.join(" ", Arrays.copyOfRange(tokens.toArray(String[]::new), 0, 7));

    assertThat(OverlapChecker.of(source).overlaps(snippet)).isFalse();
  }
}
