package bd.shromikai.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-29, R-03: the local model's strings are matched robustly, not only byte for byte. */
@Tag("unit")
@Requirement("FR-29")
class RedactorMatchingTest {

  private final Redactor redactor = new Redactor(new PiiDetector());

  private static LocalEntities persons(String... names) {
    return new LocalEntities(List.of(names), List.of(), List.of(), List.of());
  }

  private String redact(String text, LocalEntities entities) {
    return redactor.redact(text, entities, new PlaceholderMap());
  }

  @Test
  void shouldMatchIgnoringCase() {
    assertThat(redact("Signed by RAHIM UDDIN SARKER today.", persons("Rahim Uddin Sarker")))
        .isEqualTo("Signed by [PERSON_1] today.");
  }

  @Test
  void shouldMatchAcrossUnicodeNormalForms() {
    String decomposed = Normalizer.normalize("René Dupont", Normalizer.Form.NFD);
    String composed = Normalizer.normalize("René Dupont", Normalizer.Form.NFC);

    assertThat(redact("Agreed with " + decomposed + " here.", persons(composed)))
        .isEqualTo("Agreed with [PERSON_1] here.");
    assertThat(redact("Agreed with " + composed + " here.", persons(decomposed)))
        .isEqualTo("Agreed with [PERSON_1] here.");
  }

  @Test
  void shouldMatchBanglaWhenTheVowelSignIsStoredAsTwoParts() {
    String composedName = "মোহাম্মদ আলী";
    String splitText = composedName.replace("\u09CB", "\u09C7\u09BE");
    assertThat(splitText).isNotEqualTo(composedName);

    assertThat(redact("চুক্তি " + splitText + " এর সাথে।", persons(composedName)))
        .isEqualTo("চুক্তি [PERSON_1] এর সাথে।");
  }

  @Test
  void shouldRedactASurnameStandingAlone() {
    PlaceholderMap map = new PlaceholderMap();

    String out =
        redactor.redact(
            "Mr Sarker shall report to the manager.", persons("Rahim Uddin Sarker"), map);

    assertThat(out).isEqualTo("Mr [PERSON_1] shall report to the manager.");
    assertThat(map.protectedValues()).contains("Sarker");
  }

  @Test
  void shouldNotMatchANamePartInsideALongerWord() {
    assertThat(redact("Ali signed. Alibaba Group did not.", persons("Ali Hasan")))
        .isEqualTo("[PERSON_1] signed. Alibaba Group did not.");
  }

  @Test
  void shouldIgnoreTitlesAndVeryShortParts() {
    assertThat(redact("Md. Ab was at the Md office; Ab left.", persons("Md. Ab")))
        .isEqualTo("[PERSON_1] was at the Md office; Ab left.");
  }
}
