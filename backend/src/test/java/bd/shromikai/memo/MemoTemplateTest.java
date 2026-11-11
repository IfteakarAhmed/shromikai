package bd.shromikai.memo;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-55, T-18: injected HTML stays escaped; the page references no remote resource. */
@Tag("unit")
@Requirement("FR-55")
class MemoTemplateTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String EVIL =
      "<script>alert(1)</script><img src=\"https://evil.example/x.png\">";

  private JsonNode result() throws Exception {
    MemoInput input =
        new MemoInput(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "bn",
            "BASIC",
            java.time.Instant.parse("2026-11-16T10:00:00Z"),
            "সারসংক্ষেপ " + EVIL,
            List.of("পদক্ষেপ " + EVIL),
            List.of(
                new MemoInput.FindingView(
                    "f1",
                    EVIL,
                    "HIGH",
                    "LLM_ANALYSIS",
                    "VERIFIED",
                    "ব্যাখ্যা " + EVIL,
                    "",
                    List.of("c-1"),
                    "r1")),
            List.of("r1"),
            List.of("o1"),
            new MemoInput.Verification(1, 0, 0, "NONE"),
            new MemoInput.Footer("2026.11.01-1", "p7-1", "k1"));
    var memo = new MemoComposer(MAPPER).compose(input);
    return MAPPER.readTree(
        """
        {"memo": %s,
         "findings":[{"id":"f1","title":%s,"severity":"HIGH","verificationStatus":"VERIFIED",
           "citations":[{"citationId":"c-1","actCode":"XV-LAB","sectionNo":"9",
             "sectionKey":"XV-LAB:9@original","quote":%s,
             "sourceUrl":"https://example.test/act/9"}]}],
         "redlines":[{"id":"r1","rationale":%s,"diff":[
           {"op":"EQUAL","text":"The employer may end "},
           {"op":"DELETE","text":"%s"},{"op":"INSERT","text":"on 14 days notice"}]}],
         "obligations":[{"id":"o1","party":"EMPLOYER","obligation":%s,"trigger":null,"deadline":
           {"value":14,"unit":"DAYS"},"source":"BOTH",
           "citations":[{"actCode":"XV-LAB","sectionNo":"9"}]}]}
        """
            .formatted(
                memo,
                MAPPER.writeValueAsString(EVIL),
                MAPPER.writeValueAsString(EVIL),
                MAPPER.writeValueAsString(EVIL),
                "at once",
                MAPPER.writeValueAsString(EVIL)));
  }

  @Test
  void injectedHtmlStaysEscapedInEverySection() throws Exception {
    String html = new MemoHtmlRenderer().render(result());

    assertThat(html).doesNotContain("<script").doesNotContain("<img");
    assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
  }

  @Test
  void theDocumentReferencesNoRemoteResourceAndHasNoLinks() throws Exception {
    String html = new MemoHtmlRenderer().render(result());

    // The attacker's own URL survives only as inert escaped text; the page itself has none:
    assertThat(html.replace("https://evil.example/x.png", "")).doesNotContainPattern("https?://");
    assertThat(html).doesNotContainPattern("<[^>]*\\s(src|href)\\s*=").doesNotContain("<link");
    assertThat(html).doesNotContain("url(").doesNotContain("@import");
  }

  @Test
  void itCarriesTheLanguageTheCitationAndTheRedlineMarkup() throws Exception {
    String html = new MemoHtmlRenderer().render(result());

    assertThat(html).contains("<html lang=\"bn\">").contains("class=\"chip\">XV-LAB § 9");
    assertThat(html).contains("example.test/act/9").contains("<ins>on 14 days notice</ins>");
    assertThat(html).contains("<del>").contains("এটা আইনি পরামর্শ নয়");
  }
}
