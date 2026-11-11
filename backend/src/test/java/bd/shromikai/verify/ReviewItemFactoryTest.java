package bd.shromikai.verify;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.text.OverlapChecker;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.ReviewItemFactory.Draft;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-50, R-02: review items hold no 8-token sequence of the source (glossary). */
@Tag("unit")
@Requirement("FR-50")
class ReviewItemFactoryTest {

  private static final String CLAUSE =
      "ZQXJ-CANARY-0A1B2C3D The employer may end the service of the worker at any time without"
          + " giving any notice or paying any compensation whatsoever to the worker.";

  private final ReviewItemFactory factory =
      new ReviewItemFactory(Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC));

  private Draft draft(String paraphrase, String claim) {
    return new Draft(
        UUID.randomUUID(),
        "f1",
        "NOTICE_AND_RESIGNATION",
        "CONFLICT",
        "HIGH",
        "WORKER",
        List.of(new FactView("NOTICE_BY_EMPLOYER", 0.0, "DAYS")),
        paraphrase,
        "XV-LAB:999@original",
        "an invented quote about nothing in particular",
        claim == null ? null : SensitiveText.of(claim),
        List.of("SECTION_NOT_FOUND"),
        List.of("XV-LAB:9@original"),
        List.of(SensitiveText.of(CLAUSE)));
  }

  @Test
  void shouldKeepAShortParaphraseThatSharesNoWindowWithTheSource() {
    AtomicInteger regenerations = new AtomicInteger();

    ReviewItem item =
        factory.create(
            draft("The clause lets dismissal happen with no notice period.", "Notice is owed."),
            () -> {
              regenerations.incrementAndGet();
              return Optional.empty();
            });

    assertThat(item.riskSummary())
        .isEqualTo("The clause lets dismissal happen with no notice period.");
    assertThat(item.claimedClaim()).isEqualTo("Notice is owed.");
    assertThat(item.claimReduced()).isFalse();
    assertThat(regenerations).hasValue(0);
  }

  @Test
  void shouldRegenerateOnceAParaphraseWithAnEightTokenOverlap() {
    AtomicInteger regenerations = new AtomicInteger();
    String copied = "The employer may end the service of the worker at any time";

    ReviewItem item =
        factory.create(
            draft(copied, null),
            () -> {
              regenerations.incrementAndGet();
              return Optional.of("Dismissal is possible at any moment with nothing owed.");
            });

    assertThat(regenerations).hasValue(1);
    assertThat(item.riskSummary())
        .isEqualTo("Dismissal is possible at any moment with nothing owed.");
    assertThat(item.claimReduced()).isFalse();
  }

  @Test
  void shouldReduceToTheFactsOnlyFormWhenTheRegeneratedParaphraseOverlapsToo() {
    String copied = "the employer may end the service of the worker at any time without giving";
    AtomicInteger regenerations = new AtomicInteger();

    ReviewItem item =
        factory.create(
            draft(copied, null),
            () -> {
              regenerations.incrementAndGet();
              return Optional.of(copied);
            });

    assertThat(regenerations).as("regenerated once, not twice").hasValue(1);
    assertThat(item.riskSummary())
        .isEqualTo(
            "Clause type NOTICE_AND_RESIGNATION, finding kind CONFLICT, severity HIGH. Facts:"
                + " NOTICE_BY_EMPLOYER=0 DAYS.");
    assertThat(item.claimReduced()).isTrue();
  }

  @Test
  void shouldReplaceAClaimWithAnEightTokenOverlapByTheFactsOnlyForm() {
    ReviewItem item =
        factory.create(
            draft(
                "No notice is required by the clause.",
                "Section 9 is violated because the employer may end the service of the worker at any time"),
            Optional::empty);

    assertThat(item.claimedClaim()).startsWith("Clause type NOTICE_AND_RESIGNATION");
    assertThat(item.claimReduced()).isTrue();
    assertThat(item.riskSummary()).isEqualTo("No notice is required by the clause.");
  }

  @Test
  void shouldRejectTooLongTextsAndTruncateTheQuote() {
    ReviewItem item = factory.create(draft("x ".repeat(300), "y ".repeat(400)), Optional::empty);

    assertThat(item.riskSummary()).hasSizeLessThanOrEqualTo(400);
    assertThat(item.claimedClaim()).hasSizeLessThanOrEqualTo(600);
    assertThat(item.claimReduced()).isTrue();
  }

  @Test
  void shouldNeverStoreTheCanaryMarkerOrAnyWindowOfTheSource() {
    OverlapChecker source = OverlapChecker.of(CLAUSE);
    for (String hostile :
        List.of(
            CLAUSE,
            "ZQXJ-CANARY-0A1B2C3D The employer may end the service of the worker",
            "x " + CLAUSE)) {
      ReviewItem item = factory.create(draft(hostile, hostile), () -> Optional.of(hostile));

      for (String field : List.of(item.riskSummary(), item.claimedClaim(), item.claimedQuote())) {
        assertThat(field).doesNotContain("ZQXJ-CANARY");
        assertThat(source.overlaps(field)).isFalse();
      }
    }
  }

  @Test
  void shouldExpireWithTheRetentionPeriod() {
    ReviewItem item = factory.create(draft("short paraphrase here", null), Optional::empty);

    assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-11-16T10:00:00Z"));
    assertThat(item.expiresAt()).isEqualTo(Instant.parse("2026-12-16T10:00:00Z"));
  }
}
