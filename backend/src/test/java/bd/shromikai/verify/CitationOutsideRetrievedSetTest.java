package bd.shromikai.verify;

import static bd.shromikai.verify.VerifyFixtures.CLAIM_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.KEY_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.KEY_OTHER;
import static bd.shromikai.verify.VerifyFixtures.QUOTE_NOTICE;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.VerifyFixtures.Corpus;
import bd.shromikai.verify.VerifyFixtures.SupportPort;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-42, R-01: a real section that was not retrieved is rejected without a verifier call. */
@Tag("unit")
@Requirement("FR-42")
class CitationOutsideRetrievedSetTest {

  @Test
  void shouldRejectARealSectionThatIsNotInTheCandidateSet() {
    SupportPort verifier =
        new SupportPort("anthropic", r -> VerifyFixtures.verdict("SUPPORTS", 0.99));
    var models = VerifyFixtures.models(null);
    CitationVerifier citationVerifier =
        VerifyFixtures.verifier(new Corpus(), VerifyFixtures.gateway(models, verifier), models);

    // KEY_NOTICE exists, is current and its quote is exact, but only KEY_OTHER was retrieved.
    Verdict verdict =
        citationVerifier.verify(
            VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, CLAIM_NOTICE),
            VerifyFixtures.context(KEY_OTHER));

    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(verdict.result().reasonCodes()).containsExactly(ReasonCode.NOT_IN_CANDIDATES);
    assertThat(verdict.citation()).isNull();
    assertThat(verdict.result().checks().quoteMatch().outcome()).isEqualTo(CheckOutcome.SKIPPED);
    assertThat(verifier.calls).as("no support call for a rejected citation").isEmpty();
    assertThat(List.of(verdict.result().checks().support().outcome()))
        .containsExactly(CheckOutcome.SKIPPED);
  }
}
