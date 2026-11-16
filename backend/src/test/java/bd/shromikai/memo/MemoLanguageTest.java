package bd.shromikai.memo;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-55, R-11, R-12: the memo (and so the PDF) speaks one language. In Bangla every table header
 * and every enum value is Bangla; in demo mode the method section says which parts are recorded and
 * which are scripted stand-ins.
 */
@Tag("unit")
@Requirement("FR-55")
@Requirement("FR-75")
class MemoLanguageTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** English words and enum values that must not appear as text of a Bangla memo. */
  private static final Pattern ENGLISH_WORDS =
      Pattern.compile(
          "\\b(Risk|Severity|Status|Party|Obligation|When|Basis|Critical|CRITICAL|HIGH|MEDIUM|LOW"
              + "|VERIFIED|UNCERTAIN|POLICY_ONLY|EMPLOYER|WORKER|BOTH|CONTRACT|STATUTE"
              + "|SAME_FAMILY|DIFFERENT_FAMILY|MIXED|NONE|DAYS|WEEKS|MONTHS|Verified|Uncertain)\\b");

  private JsonNode result(String language, boolean recorded, String independence) throws Exception {
    MemoInput input =
        new MemoInput(
            UUID.randomUUID(),
            UUID.randomUUID(),
            language,
            "BASIC",
            Instant.parse("2026-11-16T10:00:00Z"),
            "summary text",
            List.of("a step"),
            List.of(
                new MemoInput.FindingView(
                    "f1",
                    "A risk title",
                    "CRITICAL",
                    "LLM_ANALYSIS",
                    "VERIFIED",
                    "why",
                    "",
                    List.of("c-1"),
                    "r1"),
                new MemoInput.FindingView(
                    "f2",
                    "Another title",
                    "LOW",
                    "LLM_ANALYSIS",
                    "UNCERTAIN",
                    "why",
                    "",
                    List.of(),
                    null)),
            List.of("r1"),
            List.of("o1", "o2"),
            new MemoInput.Verification(1, 1, 0, independence, recorded),
            new MemoInput.Footer("2026.11.01-1", "p7-1", "k1"));
    JsonNode memo = MAPPER.valueToTree(new MemoComposer(MAPPER).compose(input));
    return MAPPER.readTree(
        """
        {"memo": %s,
         "findings":[
          {"id":"f1","title":"A risk title","severity":"CRITICAL","verificationStatus":"VERIFIED",
           "citations":[{"citationId":"c-1","actCode":"XV-LAB","sectionNo":"9",
             "sectionKey":"XV-LAB:9@original","quote":"a quote","sourceUrl":"https://e.test/9"}]},
          {"id":"f2","title":"Another title","severity":"LOW","verificationStatus":"UNCERTAIN",
           "citations":[]}],
         "redlines":[{"id":"r1","rationale":"fair","diff":[{"op":"EQUAL","text":"text"}]}],
         "obligations":[
          {"id":"o1","party":"EMPLOYER","obligation":"pay","trigger":"each month","deadline":
             {"value":14,"unit":"DAYS"},"source":"CONTRACT","citations":[]},
          {"id":"o2","party":"BOTH","obligation":"keep records","trigger":null,"deadline":null,
             "source":"STATUTE","citations":[{"actCode":"XV-LAB","sectionNo":"9"}]}]}
        """
            .formatted(memo));
  }

  private static String text(String html) {
    return html.replaceAll("<style>.*?</style>", " ").replaceAll("<[^>]+>", " ");
  }

  @Test
  void aBanglaMemoHasBanglaTableHeadersAndEnumValues() throws Exception {
    String html = new MemoHtmlRenderer().render(result("bn", false, "SAME_FAMILY"));

    assertThat(html)
        .contains("<th>ঝুঁকি</th>", "<th>মাত্রা</th>", "<th>অবস্থা</th>")
        .contains("<th>পক্ষ</th>", "<th>দায়িত্ব</th>", "<th>কখন</th>", "<th>ভিত্তি</th>")
        .contains("গুরুতর", "✓ যাচাইকৃত", "? অনিশ্চিত", "নিয়োগকর্তা", "দুই পক্ষ", "দিন");
    assertThat(ENGLISH_WORDS.matcher(text(html)).results().map(r -> r.group()).toList())
        .as("English words left in a Bangla memo")
        .isEmpty();
  }

  @Test
  void theMethodSectionNamesTheVerifierFamilyInBangla() throws Exception {
    JsonNode memo = result("bn", false, "SAME_FAMILY").path("memo");

    assertThat(method(memo)).contains("একই মডেল পরিবারের যাচাইকারী").doesNotContain("SAME_FAMILY");
  }

  @Test
  void anEnglishMemoKeepsEnglishHeadersAndValues() throws Exception {
    String html = new MemoHtmlRenderer().render(result("en", false, "NONE"));

    assertThat(html)
        .contains("<th>Risk</th>", "<th>Severity</th>", "<th>Status</th>", "<th>Party</th>")
        .contains("<th>Obligation</th>", "<th>When</th>", "<th>Basis</th>")
        .contains("Critical", "✓ Verified", "Employer", "Both sides", "days");
  }

  @Test
  void inDemoModeTheMethodSectionSaysWhatIsRecordedAndWhatIsScripted() throws Exception {
    for (String language : List.of("bn", "en")) {
      JsonNode demo = result(language, true, "SAME_FAMILY").path("memo");
      JsonNode normal = result(language, false, "SAME_FAMILY").path("memo");
      String demoMethod = method(demo);

      assertThat(demoMethod).contains(language.equals("bn") ? "স্ক্রিপ্ট করা" : "scripted");
      assertThat(demoMethod).contains(language.equals("bn") ? "রেকর্ড করা" : "recorded");
      assertThat(method(normal)).doesNotContain(language.equals("bn") ? "স্ক্রিপ্ট" : "scripted");
    }
  }

  private static String method(JsonNode memo) {
    for (JsonNode section : memo.path("sections")) {
      if ("VERIFICATION_AND_METHOD".equals(section.path("kind").asText())) {
        return section.path("blocks").toString();
      }
    }
    return "";
  }
}
