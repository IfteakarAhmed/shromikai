package bd.shromikai.verify;

import static bd.shromikai.verify.VerifyFixtures.KEY_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.NOTICE_TEXT;
import static bd.shromikai.verify.VerifyFixtures.QUOTE_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.TODAY;
import static bd.shromikai.verify.VerifyFixtures.llmClaim;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.VerifyFixtures.Corpus;
import bd.shromikai.verify.internal.ExistenceCheck;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-42: existence and currency of a cited section (architecture §7.3 check a). */
@Tag("unit")
@Requirement("FR-42")
class ExistenceCheckTest {

  private final Corpus corpus =
      new Corpus()
          .add(
              VerifyFixtures.section(
                  "XV-LAB:9@amend-2020", "XV-LAB", "9", "amend-2020", false, null, NOTICE_TEXT),
              "XV",
              "IN_FORCE")
          .add(
              VerifyFixtures.section(
                  "XV-OLD:1@original", "XV-OLD", "1", "original", true, null, NOTICE_TEXT),
              "XV",
              "REPEALED")
          .add(
              VerifyFixtures.section(
                  "XV-FOREIGN:1@original", "XV-FOREIGN", "1", "original", true, null, NOTICE_TEXT),
              "ZZ",
              "IN_FORCE")
          .add(
              VerifyFixtures.section(
                  "XV-FUTURE:1@original",
                  "XV-FUTURE",
                  "1",
                  "original",
                  true,
                  TODAY.plusDays(1),
                  NOTICE_TEXT),
              "XV",
              "IN_FORCE");
  private final ExistenceCheck check = new ExistenceCheck(corpus);

  private ReasonCode failure(String key, String... candidates) {
    return check
        .check(
            llmClaim(key, QUOTE_NOTICE, "claim text long enough"),
            VerifyFixtures.context(candidates))
        .failure();
  }

  @Test
  void shouldPassACurrentSectionInThePackAndTheCandidateSet() {
    var outcome =
        check.check(
            llmClaim(KEY_NOTICE, QUOTE_NOTICE, "claim text long enough"),
            VerifyFixtures.context(KEY_NOTICE));

    assertThat(outcome.passed()).isTrue();
    assertThat(outcome.lookup().section().sectionKey()).isEqualTo(KEY_NOTICE);
  }

  @Test
  void shouldRejectAnUnknownSection() {
    assertThat(failure("XV-LAB:999@original", "XV-LAB:999@original"))
        .isEqualTo(ReasonCode.SECTION_NOT_FOUND);
  }

  @Test
  void shouldRejectAMalformedKey() {
    assertThat(failure("not a key", "not a key")).isEqualTo(ReasonCode.SECTION_NOT_FOUND);
  }

  @Test
  void shouldRejectASupersededVersion() {
    assertThat(failure("XV-LAB:9@amend-2020", "XV-LAB:9@amend-2020"))
        .isEqualTo(ReasonCode.SECTION_NOT_CURRENT);
  }

  @Test
  void shouldRejectARepealedAct() {
    assertThat(failure("XV-OLD:1@original", "XV-OLD:1@original"))
        .isEqualTo(ReasonCode.SECTION_NOT_CURRENT);
  }

  @Test
  void shouldRejectASectionNotYetEffective() {
    assertThat(failure("XV-FUTURE:1@original", "XV-FUTURE:1@original"))
        .isEqualTo(ReasonCode.SECTION_NOT_CURRENT);
  }

  @Test
  void shouldRejectAnActOutsideThePack() {
    assertThat(failure("XV-FOREIGN:1@original", "XV-FOREIGN:1@original"))
        .isEqualTo(ReasonCode.ACT_NOT_IN_PACK);
  }

  @Test
  void shouldRejectAClaimedActThatDoesNotMatchTheSection() {
    ClaimedCitation claim =
        new ClaimedCitation(
            java.util.UUID.randomUUID(),
            KEY_NOTICE,
            "XV-OTHER",
            "9",
            QUOTE_NOTICE,
            SensitiveText.of("claim text long enough"),
            "en",
            CitationOrigin.LLM,
            null,
            null);

    assertThat(check.check(claim, VerifyFixtures.context(KEY_NOTICE)).failure())
        .isEqualTo(ReasonCode.ACT_MISMATCH);
  }

  @Test
  void shouldNotRequireCandidatesForNonLlmOrigins() {
    ClaimedCitation external =
        ClaimedCitation.of(
            KEY_NOTICE,
            QUOTE_NOTICE,
            SensitiveText.of("claim text long enough"),
            "en",
            CitationOrigin.EXTERNAL,
            null,
            null);

    assertThat(
            check
                .check(external, new VerificationContext(TODAY, Set.of("XV"), "r", Set.of(), 1))
                .passed())
        .isTrue();
  }
}
