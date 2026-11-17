package bd.shromikai.privacy;

import static bd.shromikai.testsupport.TestGateways.gateway;
import static bd.shromikai.testsupport.TestGateways.json;
import static bd.shromikai.testsupport.TestGateways.port;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.trace.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-28, FR-29, FR-30, R-03: the local model labels generic Bangla role words (owner, employee,
 * signature) as names. They must stay in the text, because an inflected form ("মালিকের") cannot be
 * redacted by the whole-word matcher and would make the egress guard block the whole analysis; real
 * names and every deterministic identifier must still be redacted and still be blocked when they
 * leak. All values are invented.
 */
@Tag("unit")
@Requirement("FR-28")
@Requirement("FR-29")
@Requirement("FR-30")
class BanglaRoleWordsFailClosedTest {

  private static final String EMPLOYER = "রহিম নমুনা সরকার";
  private static final String WORKER = "মোছাঃ নমুনা বেগম";
  private static final String ORG = "উদাহরণ গার্মেন্টস লিমিটেড";
  private static final String NID = "১২৩৪৫৬৭৮৯০১২৩";
  private static final String PHONE = "০১৭০০-০০০১০১";
  private static final String EMAIL = "namuna@example.org";

  private static final List<String> PARAGRAPHS =
      List.of(
          "এই চুক্তি " + ORG + " অতঃপর \"মালিক\" এবং " + WORKER + " অতঃপর \"কর্মচারী\"-এর মধ্যে।",
          "জাতীয় পরিচয়পত্র নং " + NID + ", মোবাইল " + PHONE + ", ইমেইল " + EMAIL + "।",
          "মালিকের অনুমতি ছাড়া কর্মচারীর ছুটি হবে না; মালিককে নোটিশ দিতে হবে।",
          "স্বাক্ষর: মালিকের পক্ষে " + EMPLOYER + "; কর্মচারী: " + WORKER + "।");

  /** The local model as observed in P10: real names, plus role words labelled as persons. */
  private static final String MODEL_ANSWER =
      "{\"status\":\"OK\",\"reason\":\"NONE\",\"persons\":[\"মালিক\",\"কর্মচারী\",\"স্বাক্ষর\","
          + "\"রহিম নমুনা সরকার\",\"মোছাঃ নমুনা বেগম\"],\"organisations\":[\"উদাহরণ গার্মেন্টস লিমিটেড\"],"
          + "\"addresses\":[],\"employee_ids\":[]}";

  private final PrivacyEgressGuard guard =
      new PrivacyEgressGuard(
          new PiiDetector(), mock(AuditService.class), new SimpleMeterRegistry());

  private static PrivacyShield shield(String modelAnswer) {
    ChatPort ollama = port("ollama", request -> json(modelAnswer));
    return new PrivacyShield(
        new LocalEntityExtractor(gateway(ollama)),
        new Redactor(new PiiDetector()),
        new InjectionDetector());
  }

  private static String text(List<RedactedParagraph> redacted) {
    return String.join("\n", redacted.stream().map(p -> p.text().reveal()).toList());
  }

  private static ChatRequest cloudRequest(String user, PlaceholderMap map) {
    return new ChatRequest(
        "clause-classify",
        1,
        ModelRole.FAST,
        "claude-haiku-5-5",
        "Classify clauses.",
        SensitiveText.of(user),
        new ObjectMapper().createObjectNode(),
        100,
        Duration.ofSeconds(5),
        null,
        null,
        map);
  }

  @Test
  void shouldKeepGenericRoleWordsAndRedactEveryRealIdentifier() {
    PlaceholderMap map = new PlaceholderMap("job-1");

    String redacted = text(shield(MODEL_ANSWER).redact(PARAGRAPHS, map));

    assertThat(redacted)
        .contains("মালিক", "কর্মচারী", "স্বাক্ষর", "মালিকের", "কর্মচারীর", "মালিককে")
        .doesNotContain(EMPLOYER, WORKER, ORG, NID, PHONE, EMAIL, "নমুনা", "রহিম", "বেগম");
    assertThat(redacted).contains("[PERSON_1]", "[PERSON_2]", "[ORG_1]", "[NID_1]", "[PHONE_1]");
    assertThat(redacted).contains("[EMAIL_1]");
    assertThat(map.protectedValues()).doesNotContain("মালিক", "কর্মচারী", "স্বাক্ষর");
    assertThat(map.protectedValues()).contains(EMPLOYER, WORKER, ORG, NID, PHONE, EMAIL);
  }

  @Test
  void shouldNotBlockTheRedactedTextAtTheEgressGuard() {
    PlaceholderMap map = new PlaceholderMap("job-2");
    String redacted = text(shield(MODEL_ANSWER).redact(PARAGRAPHS, map));

    assertThatCode(() -> guard.check(cloudRequest(redacted, map))).doesNotThrowAnyException();
  }

  @Test
  void shouldStillBlockEachRealValueThatLeaksIntoARequest() {
    PlaceholderMap map = new PlaceholderMap("job-3");
    String redacted = text(shield(MODEL_ANSWER).redact(PARAGRAPHS, map));

    List<String> notBlocked = new java.util.ArrayList<>();
    for (String leak : List.of(EMPLOYER, WORKER, ORG, NID, PHONE, EMAIL)) {
      try {
        guard.check(cloudRequest(redacted + "\n" + leak, map));
        notBlocked.add(leak);
      } catch (EgressBlockedException expected) {
        // blocked, as required
      }
    }

    assertThat(notBlocked).as("leaks the guard let through").isEmpty();
  }

  @Test
  void shouldRedactDeterministicIdentifiersWhenTheModelFindsOnlyRoleWords() {
    String onlyRoleWords =
        "{\"status\":\"OK\",\"reason\":\"NONE\",\"persons\":[\"মালিক\",\"কর্মচারী\"],"
            + "\"organisations\":[],\"addresses\":[],\"employee_ids\":[]}";
    PlaceholderMap map = new PlaceholderMap("job-4");

    String redacted = text(shield(onlyRoleWords).redact(PARAGRAPHS, map));

    assertThat(redacted).doesNotContain(NID, PHONE, EMAIL).contains("মালিকের", "কর্মচারীর");
    assertThatThrownBy(() -> guard.check(cloudRequest(redacted + " " + NID, map)))
        .isInstanceOf(EgressBlockedException.class);
  }

  @Test
  void shouldRedactAValueThatOnlyContainsARoleWordBesideARealName() {
    String model =
        "{\"status\":\"OK\",\"reason\":\"NONE\",\"persons\":[\"মালিক রহিম নমুনা সরকার\"],"
            + "\"organisations\":[],\"addresses\":[],\"employee_ids\":[]}";
    PlaceholderMap map = new PlaceholderMap("job-5");

    String redacted = text(shield(model).redact(List.of("স্বাক্ষর: মালিক রহিম নমুনা সরকার।"), map));

    assertThat(redacted).doesNotContain("রহিম", "নমুনা").contains("স্বাক্ষর");
    assertThat(map.protectedValues()).contains("মালিক রহিম নমুনা সরকার");
  }
}
