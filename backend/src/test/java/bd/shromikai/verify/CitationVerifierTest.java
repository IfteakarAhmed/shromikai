package bd.shromikai.verify;

import static bd.shromikai.verify.VerifyFixtures.CLAIM_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.KEY_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.NOTICE_TEXT;
import static bd.shromikai.verify.VerifyFixtures.QUOTE_NOTICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.VerifyFixtures.Corpus;
import bd.shromikai.verify.VerifyFixtures.SupportPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-42 to FR-45 end to end over stub ports: checks, order, provenance and the stored quote. */
@Tag("unit")
@Requirement("FR-45")
@Requirement("FR-47")
class CitationVerifierTest {

  private final ShromikaiProperties.Models claudeOnly = VerifyFixtures.models(null);

  private CitationVerifier verifier(SupportPort port) {
    return VerifyFixtures.verifier(
        new Corpus(), VerifyFixtures.gateway(claudeOnly, port), claudeOnly);
  }

  private static SupportPort answering(String label, double confidence) {
    return new SupportPort("anthropic", r -> VerifyFixtures.verdict(label, confidence));
  }

  @Test
  void shouldVerifyACitationWhenAllThreeChecksPass() {
    Verdict verdict =
        verifier(answering("SUPPORTS", 0.9))
            .verify(
                VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, CLAIM_NOTICE),
                VerifyFixtures.context(KEY_NOTICE));

    VerificationResult result = verdict.result();
    assertThat(verdict.verified()).isTrue();
    assertThat(result.checks().existence().outcome()).isEqualTo(CheckOutcome.PASS);
    assertThat(result.checks().quoteMatch().similarity()).isEqualTo(1.0);
    assertThat(result.checks().support().label()).isEqualTo(SupportLabel.SUPPORTS);
    assertThat(result.checks().support().independence())
        .isEqualTo(VerifierIndependence.SAME_FAMILY);
    assertThat(result.attempt()).isEqualTo(1);
    assertThat(result.corpusRelease()).isEqualTo("2026.11.01-1");
    assertThat(verdict.citation().sectionKey()).isEqualTo(KEY_NOTICE);
  }

  @Test
  void shouldShowTheStoredStatuteTextNotTheModelsCopy() {
    String sloppy = "shall give a worker written notice of dismissal at least fourteen dayz";
    Verdict verdict =
        verifier(answering("SUPPORTS", 0.9))
            .verify(
                VerifyFixtures.llmClaim(KEY_NOTICE, sloppy, CLAIM_NOTICE),
                VerifyFixtures.context(KEY_NOTICE));

    assertThat(verdict.verified()).isTrue();
    assertThat(NOTICE_TEXT).contains(verdict.citation().quote());
    assertThat(verdict.citation().quote()).doesNotContain("dayz");
  }

  @Test
  void shouldMarkPartialSupportUncertainAndShowNoCitation() {
    Verdict verdict =
        verifier(answering("PARTIAL", 0.95))
            .verify(
                VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, CLAIM_NOTICE),
                VerifyFixtures.context(KEY_NOTICE));

    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.UNCERTAIN);
    assertThat(verdict.result().reasonCodes()).containsExactly(ReasonCode.SUPPORT_PARTIAL);
    assertThat(verdict.citation()).isNull();
  }

  @Test
  void shouldRejectAnInventedQuoteWithoutCallingTheVerifier() {
    SupportPort port = answering("SUPPORTS", 0.99);

    Verdict verdict =
        verifier(port)
            .verify(
                VerifyFixtures.llmClaim(
                    KEY_NOTICE,
                    "every worker is entitled to a company car after one year of service",
                    CLAIM_NOTICE),
                VerifyFixtures.context(KEY_NOTICE));

    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(verdict.result().reasonCodes()).containsExactly(ReasonCode.QUOTE_NOT_FOUND);
    assertThat(port.calls).isEmpty();
  }

  @Test
  void shouldRejectAQuoteThatIsTooShortOrTooLong() {
    CitationVerifier verifier = verifier(answering("SUPPORTS", 0.9));

    assertThat(
            verifier
                .verify(
                    VerifyFixtures.llmClaim(KEY_NOTICE, "written notice", CLAIM_NOTICE),
                    VerifyFixtures.context(KEY_NOTICE))
                .result()
                .reasonCodes())
        .containsExactly(ReasonCode.QUOTE_TOO_SHORT);
    assertThat(
            verifier
                .verify(
                    VerifyFixtures.llmClaim(KEY_NOTICE, "x ".repeat(200), CLAIM_NOTICE),
                    VerifyFixtures.context(KEY_NOTICE))
                .result()
                .reasonCodes())
        .containsExactly(ReasonCode.QUOTE_TOO_LONG);
  }

  @Test
  void shouldDegradeToUncertain_neverToVerified_whenNoVerifierIsReachable() {
    SupportPort down =
        new SupportPort(
            "anthropic",
            r -> {
              throw new UpstreamUnavailableException("anthropic-http-503", 503, null);
            });

    Verdict verdict =
        verifier(down)
            .verify(
                VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, CLAIM_NOTICE),
                VerifyFixtures.context(KEY_NOTICE));

    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.UNCERTAIN);
    assertThat(verdict.result().reasonCodes()).containsExactly(ReasonCode.VERIFIER_UNAVAILABLE);
    assertThat(verdict.citation()).isNull();
  }

  @Test
  void shouldVerifyARuleCitationFromAReviewedSourceWithoutCallingTheVerifier() {
    SupportPort port = answering("UNRELATED", 1.0);
    ClaimedCitation claim =
        ClaimedCitation.of(
            KEY_NOTICE,
            QUOTE_NOTICE,
            SensitiveText.of("rule claim long enough"),
            "en",
            CitationOrigin.RULE,
            "xx-notice-min",
            new Provenance("xx-notice-min", "author@example.test", "reviewer@example.test", true));

    Verdict verdict =
        verifier(port)
            .verify(
                claim,
                new VerificationContext(
                    VerifyFixtures.TODAY, java.util.Set.of("XV"), "r", java.util.Set.of(), 1));

    assertThat(verdict.verified()).isTrue();
    assertThat(verdict.result().checks().support().independence())
        .isEqualTo(VerifierIndependence.RULE_PROVENANCE);
    assertThat(port.calls).isEmpty();
  }

  @Test
  void shouldRefuseARuleCitationWithoutTwoPersonReview() {
    for (Provenance weak :
        new Provenance[] {
          null,
          new Provenance("r", "same@example.test", "same@example.test", true),
          new Provenance("r", "a@example.test", null, true),
          new Provenance("r", "a@example.test", "b@example.test", false)
        }) {
      assertThatThrownBy(
              () ->
                  ClaimedCitation.of(
                      KEY_NOTICE,
                      QUOTE_NOTICE,
                      SensitiveText.of("rule claim long enough"),
                      "en",
                      CitationOrigin.RULE,
                      "r",
                      weak))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void shouldVerifyAReviewerCitationAsHuman() {
    ClaimedCitation claim =
        ClaimedCitation.of(
            KEY_NOTICE,
            QUOTE_NOTICE,
            SensitiveText.of("reviewer claim long enough"),
            "en",
            CitationOrigin.REVIEWER,
            null,
            null);

    Verdict verdict =
        verifier(answering("UNRELATED", 1.0))
            .verify(
                claim,
                new VerificationContext(
                    VerifyFixtures.TODAY, java.util.Set.of("XV"), "r", java.util.Set.of(), 1));

    assertThat(verdict.verified()).isTrue();
    assertThat(verdict.result().checks().support().independence())
        .isEqualTo(VerifierIndependence.HUMAN);
  }

  @Test
  void shouldNeverCreateAVerifiedCitationFromANonVerifiedResult() {
    Verdict uncertain =
        verifier(answering("PARTIAL", 0.9))
            .verify(
                VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, CLAIM_NOTICE),
                VerifyFixtures.context(KEY_NOTICE));

    assertThatThrownBy(
            () ->
                new VerifiedCitation(
                    new Corpus().find(KEY_NOTICE).orElseThrow().section(),
                    QUOTE_NOTICE,
                    uncertain.result()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldVerifyManyCitationsInParallelAndKeepTheirOrder() {
    ShromikaiProperties.Models models = claudeOnly;
    SupportPort port = answering("SUPPORTS", 0.9);
    ModelGateway gateway = VerifyFixtures.gateway(models, port);
    CitationVerifier verifier = VerifyFixtures.verifier(new Corpus(), gateway, models);
    List<ClaimedCitation> claims = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      claims.add(
          i % 2 == 0
              ? VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, CLAIM_NOTICE)
              : VerifyFixtures.llmClaim("XV-LAB:404@original", QUOTE_NOTICE, CLAIM_NOTICE));
    }

    List<Verdict> verdicts =
        verifier.verifyAll(claims, VerifyFixtures.context(KEY_NOTICE, "XV-LAB:404@original"));

    assertThat(verdicts).hasSize(20);
    for (int i = 0; i < 20; i++) {
      assertThat(verdicts.get(i).result().citationId()).isEqualTo(claims.get(i).citationId());
      assertThat(verdicts.get(i).verified()).isEqualTo(i % 2 == 0);
    }
    var json = new ObjectMapper().findAndRegisterModules().valueToTree(verdicts.get(0).citation());
    assertThat(json.path("sectionKey").asText()).isEqualTo(KEY_NOTICE);
    assertThat(json.path("verification").path("status").asText()).isEqualTo("VERIFIED");
  }
}
