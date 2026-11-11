package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.privacy.InjectionDetector;
import bd.shromikai.privacy.LocalEntities;
import bd.shromikai.privacy.LocalEntityExtractor;
import bd.shromikai.privacy.PiiDetector;
import bd.shromikai.privacy.PrivacyShield;
import bd.shromikai.privacy.Redactor;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.LocalModelUnavailableException;
import bd.shromikai.testsupport.AnalysisHarness;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-64, R-03, architecture §10.7: the MCP claim is redacted locally and fails closed. */
@Tag("unit")
@Requirement("FR-64")
class ClaimRedactionServiceTest {

  private static ClaimRedactionService service(ChatPort local) {
    var gateway = TestGateways.gateway(local);
    return new ClaimRedactionService(
        new PrivacyShield(
            new LocalEntityExtractor(gateway),
            new Redactor(new PiiDetector()),
            new InjectionDetector()));
  }

  @Test
  void deterministicIdentifiersAndLocalModelNamesBecomePlaceholders() {
    LocalEntities found =
        new LocalEntities(List.of("Rahim Uddin"), List.of(), List.of(), List.of());
    ClaimRedactionService service = service(AnalysisHarness.ollamaReturning(found));
    String claim =
        "Rahim Uddin, NID 1234567890123, phone 01712345678 and mail rahim@example.test are"
            + " dismissed without the notice the section requires.";

    try (ClaimRedactionService.RedactedClaim redacted = service.redact(claim)) {
      String text = redacted.text().reveal();
      assertThat(text)
          .doesNotContain("Rahim")
          .doesNotContain("1234567890123")
          .doesNotContain("01712345678")
          .doesNotContain("rahim@example.test")
          .contains("[PERSON_1]", "[NID_1]", "[PHONE_1]", "[EMAIL_1]");
      assertThat(redacted.context().protectedValues()).contains("Rahim Uddin", "01712345678");
    }
  }

  @Test
  void theClaimLanguageIsBanglaWhenTheTextIsBangla() {
    ClaimRedactionService service = service(AnalysisHarness.ollamaReturning(LocalEntities.NONE));

    try (var bangla = service.redact("চাকরি শেষ করতে চৌদ্দ দিনের নোটিশ লাগে বলে ধারাটি বলছে।")) {
      assertThat(bangla.language()).isEqualTo("bn");
    }
    try (var english = service.redact("The section requires fourteen days of written notice.")) {
      assertThat(english.language()).isEqualTo("en");
    }
  }

  @Test
  void whenTheLocalModelIsDownItFailsClosedWithoutReturningText() {
    ChatPort down =
        new ChatPort() {
          public String provider() {
            return "ollama";
          }

          public ChatResponse call(ChatRequest request) {
            throw new LocalModelUnavailableException("ollama-unavailable");
          }
        };

    assertThatThrownBy(() -> service(down).redact("A claim of twenty characters or more."))
        .isInstanceOf(LocalModelUnavailableException.class);
  }

  @Test
  void closingForgetsEveryValue() {
    LocalEntities found = new LocalEntities(List.of("Karim Mia"), List.of(), List.of(), List.of());
    var redacted =
        service(AnalysisHarness.ollamaReturning(found))
            .redact("Karim Mia was paid late and the section says wages are due in seven days.");

    redacted.close();

    assertThat(redacted.context().protectedValues()).isEmpty();
  }
}
