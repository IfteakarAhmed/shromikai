package bd.shromikai.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-29: redact then restore gives the original; placeholders are stable within a document. */
@Tag("unit")
@Requirement("FR-29")
class RedactionRoundTripTest {

  private final Redactor redactor = new Redactor(new PiiDetector());

  private static final LocalEntities ENTITIES =
      new LocalEntities(
          List.of("Rahim Uddin Sarker", "Karima Begum"),
          List.of("Example Garments Ltd."),
          List.of("House 12, Road 5, Fictional Town"),
          List.of("EMP-4471"));

  private static final List<String> DOCUMENT =
      List.of(
          "This agreement is between Example Garments Ltd. and Rahim Uddin Sarker, NID No: 1234567890.",
          "Rahim Uddin Sarker lives at House 12, Road 5, Fictional Town; phone 01712345678.",
          "The wage is BDT 25,000 per month from 01/01/2026 for 6 months. Employee EMP-4471.",
          "Witness: Karima Begum. Rahim Uddin Sarker signs below. Mail rahim@example.org.");

  @Test
  void shouldRestoreTheOriginalTextAfterRedaction() {
    PlaceholderMap map = new PlaceholderMap("a1");

    List<String> redacted = redactor.redact(DOCUMENT, ENTITIES, map);

    assertThat(redacted).isNotEqualTo(DOCUMENT);
    List<String> restored = redacted.stream().map(map::restore).toList();
    assertThat(restored).isEqualTo(DOCUMENT);
  }

  @Test
  void shouldGiveTheSameNameOnePlaceholderEverywhere() {
    PlaceholderMap map = new PlaceholderMap();

    List<String> redacted = redactor.redact(DOCUMENT, ENTITIES, map);

    String all = String.join("\n", redacted);
    assertThat(all.split("\\[PERSON_1]", -1)).hasSize(4); // three occurrences of the first name
    assertThat(all).contains("[PERSON_2]").doesNotContain("[PERSON_3]");
    assertThat(all)
        .contains("[ORG_1]", "[ADDRESS_1]", "[NID_1]", "[PHONE_1]", "[EMAIL_1]", "[EMPLOYEE_ID_1]");
  }

  @Test
  void shouldNumberPlaceholdersByFirstOccurrenceInDocumentOrder() {
    PlaceholderMap map = new PlaceholderMap();

    List<String> redacted = redactor.redact(DOCUMENT, ENTITIES, map);

    // Rahim Uddin Sarker is met before Karima Begum in the document, so he is PERSON_1.
    assertThat(map.original("[PERSON_1]")).isEqualTo("Rahim Uddin Sarker");
    assertThat(map.original("[PERSON_2]")).isEqualTo("Karima Begum");
    assertThat(redacted.get(3)).startsWith("Witness: [PERSON_2]");
  }

  @Test
  void shouldKeepMoneyDurationsAndEmploymentDates() {
    PlaceholderMap map = new PlaceholderMap();

    String redacted = redactor.redact(DOCUMENT, ENTITIES, map).get(2);

    assertThat(redacted).contains("BDT 25,000 per month", "01/01/2026", "6 months");
  }

  @Test
  void shouldNotLeaveAnyOriginalValueInTheRedactedText() {
    PlaceholderMap map = new PlaceholderMap();

    String all = String.join("\n", redactor.redact(DOCUMENT, ENTITIES, map));

    for (String value : map.protectedValues()) {
      assertThat(all).doesNotContain(value);
    }
  }

  @Test
  void shouldIgnoreModelStringsThatDoNotOccurAndTooShortOnes() {
    PlaceholderMap map = new PlaceholderMap();
    var entities = new LocalEntities(List.of("Nobody Here", "A"), List.of(), List.of(), List.of());

    List<String> redacted = redactor.redact(List.of("Plain clause about A leave."), entities, map);

    assertThat(redacted).containsExactly("Plain clause about A leave.");
    assertThat(map.size()).isZero();
  }

  @Test
  void shouldRoundTripBanglaNamesAndDigits() {
    PlaceholderMap map = new PlaceholderMap();
    var entities =
        new LocalEntities(
            List.of("রহিম উদ্দিন"), List.of("উদাহরণ গার্মেন্টস লিমিটেড"), List.of(), List.of());
    List<String> doc =
        List.of(
            "এই চুক্তি উদাহরণ গার্মেন্টস লিমিটেড ও রহিম উদ্দিনের মধ্যে। ফোন ০১৭১২৩৪৫৬৭৮।",
            "রহিম উদ্দিন মাসিক ২৫,০০০ টাকা বেতন পাবেন।");

    List<String> redacted = redactor.redact(doc, entities, map);

    assertThat(redacted.get(0)).contains("[ORG_1]", "[PERSON_1]", "[PHONE_1]");
    assertThat(redacted.get(1)).contains("২৫,০০০ টাকা");
    assertThat(redacted.stream().map(map::restore).toList()).isEqualTo(doc);
  }

  @Test
  void shouldPreferTheLongerSpanWhenAModelStringOverlapsADetectorSpan() {
    PlaceholderMap map = new PlaceholderMap();
    var entities =
        new LocalEntities(List.of(), List.of("Acme 01712345678 Traders"), List.of(), List.of());

    String redacted = redactor.redact("Employer: Acme 01712345678 Traders signs.", entities, map);

    assertThat(redacted).isEqualTo("Employer: [ORG_1] signs.");
    assertThat(map.restore(redacted)).isEqualTo("Employer: Acme 01712345678 Traders signs.");
  }

  @Test
  void shouldMaskTheMapInToString() {
    PlaceholderMap map = new PlaceholderMap();
    map.placeholderFor(PiiType.PERSON, "Secret Name");

    assertThat(map.toString()).doesNotContain("Secret").contains("masked");
    assertThat(
            new LocalEntities(List.of("Secret Name"), List.of(), List.of(), List.of()).toString())
        .doesNotContain("Secret");
  }

  @Test
  void shouldForgetEveryValueWhenCleared() {
    PlaceholderMap map = new PlaceholderMap();
    map.placeholderFor(PiiType.PERSON, "Secret Name");

    map.clear();

    assertThat(map.size()).isZero();
    assertThat(map.protectedValues()).isEmpty();
    assertThat(map.restore("[PERSON_1]")).isEqualTo("[PERSON_1]");
  }
}
