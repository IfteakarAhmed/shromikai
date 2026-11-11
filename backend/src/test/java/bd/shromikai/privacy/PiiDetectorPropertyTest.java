package bd.shromikai.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.text.BanglaNormalizer;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Tag;

/** FR-28: jqwik generators for phone numbers and NIDs. */
@Tag("unit")
@Requirement("FR-28")
class PiiDetectorPropertyTest {

  private final PiiDetector detector = new PiiDetector();

  @Provide
  Arbitrary<String> phones() {
    Arbitrary<String> prefix = Arbitraries.of("+880", "880", "0");
    Arbitrary<String> operator = Arbitraries.integers().between(3, 9).map(i -> "1" + i);
    Arbitrary<String> head = Arbitraries.strings().numeric().ofLength(2);
    Arbitrary<String> tail = Arbitraries.strings().numeric().ofLength(6);
    Arbitrary<String> separator = Arbitraries.of("", "-");
    return Combinators.combine(prefix, operator, head, separator, tail)
        .as((p, o, h, sep, t) -> p + o + h + sep + t);
  }

  @Provide
  Arbitrary<String> nids() {
    return Arbitraries.of(10, 13, 17)
        .flatMap(n -> Arbitraries.strings().numeric().ofLength(n).filter(s -> s.charAt(0) != '0'));
  }

  @Property(tries = 300)
  void shouldDetectEveryGeneratedPhoneNumberInLatinAndBanglaDigits(@ForAll("phones") String phone) {
    for (String variant : List.of(phone, BanglaNormalizer.toBanglaDigits(phone))) {
      String text = "Contact: " + variant + " during office hours";

      List<PiiMatch> matches = detector.detect(text);

      assertThat(matches).hasSize(1);
      assertThat(matches.get(0).type()).isEqualTo(PiiType.PHONE);
      assertThat(text.substring(matches.get(0).start(), matches.get(0).end())).isEqualTo(variant);
    }
  }

  @Property(tries = 300)
  void shouldDetectEveryGeneratedNidNearItsKeyword(@ForAll("nids") String nid) {
    for (String variant : List.of(nid, BanglaNormalizer.toBanglaDigits(nid))) {
      String text = "NID No: " + variant + " (verified)";

      List<PiiMatch> matches = detector.detect(text);

      assertThat(matches).hasSize(1);
      assertThat(matches.get(0).type()).isEqualTo(PiiType.NID);
      assertThat(text.substring(matches.get(0).start(), matches.get(0).end())).isEqualTo(variant);
    }
  }

  @Property(tries = 300)
  void shouldReturnValidNonOverlappingSpansForAnyText(@ForAll String text) {
    List<PiiMatch> matches = detector.detect(text);

    int end = 0;
    for (PiiMatch match : matches) {
      assertThat(match.start()).isGreaterThanOrEqualTo(end);
      assertThat(match.end()).isLessThanOrEqualTo(text.length());
      end = match.end();
    }
  }
}
