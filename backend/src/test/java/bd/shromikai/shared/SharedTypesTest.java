package bd.shromikai.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("FR-02")
class SharedTypesTest {

  @Test
  void shouldMaskContent_whenSensitiveTextIsPrinted() {
    SensitiveText text = SensitiveText.of("চুক্তির গোপন অংশ");

    assertThat(text.toString())
        .isEqualTo("SensitiveText[masked, length=16]")
        .doesNotContain("চুক্তি");
    assertThat(text.reveal()).isEqualTo("চুক্তির গোপন অংশ");
    assertThat(text)
        .isEqualTo(SensitiveText.of("চুক্তির গোপন অংশ"))
        .hasSameHashCodeAs(SensitiveText.of("চুক্তির গোপন অংশ"));
    assertThat(text.isEmpty()).isFalse();
    assertThat(text.length()).isEqualTo(16);
  }

  @Test
  void shouldHaveUniqueCodesAndBothTitles_forEveryProblemType() {
    assertThat(Arrays.stream(ProblemType.values()).map(ProblemType::code)).doesNotHaveDuplicates();
    for (ProblemType type : ProblemType.values()) {
      assertThat(type.typeUri()).matches("urn:shromikai:problem:[a-z0-9-]+");
      assertThat(type.title(Locale.ENGLISH)).isNotBlank();
      assertThat(type.title(Locale.forLanguageTag("bn")))
          .isNotBlank()
          .isNotEqualTo(type.title(Locale.ENGLISH));
      assertThat(type.status()).isBetween(400, 599);
    }
  }

  @Test
  void shouldMapStatusesToProblemTypes() {
    assertThat(ProblemType.forStatus(404)).isEqualTo(ProblemType.NOT_FOUND);
    assertThat(ProblemType.forStatus(405)).isEqualTo(ProblemType.METHOD_NOT_ALLOWED);
    assertThat(ProblemType.forStatus(418)).isEqualTo(ProblemType.VALIDATION_FAILED);
    assertThat(ProblemType.forStatus(502)).isEqualTo(ProblemType.INTERNAL_ERROR);
    assertThat(ProblemType.forStatus(401)).isEqualTo(ProblemType.INVALID_TOKEN);
  }

  @Test
  void shouldExposeDetailOnlyWhenGiven() {
    assertThat(new ProblemException(ProblemType.NOT_FOUND).detail()).isNull();
    assertThat(new ProblemException(ProblemType.NOT_FOUND, "gone").detail()).isEqualTo("gone");
  }

  @Test
  void shouldHashRenderedRequestStably_andValidateFields() {
    ChatRequest request = request("hello");

    assertThat(request.renderedSha256())
        .isEqualTo("de5740ed4982cd3da3009c2280f84e3b080df4d5ebb9cee66b5022812210d590")
        .isNotEqualTo(request("hello!").renderedSha256());
    assertThatThrownBy(
            () ->
                new ChatRequest(
                    "p",
                    0,
                    ModelRole.FAST,
                    "m",
                    "s",
                    SensitiveText.of("u"),
                    new ObjectMapper().createObjectNode(),
                    1,
                    Duration.ZERO,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(request.toString()).doesNotContain("hello");
  }

  private static ChatRequest request(String user) {
    return new ChatRequest(
        "xx-echo",
        1,
        ModelRole.FAST,
        "claude-haiku-5-5",
        "You echo the input as JSON.",
        SensitiveText.of(user),
        new ObjectMapper().createObjectNode(),
        100,
        Duration.ofSeconds(5),
        null,
        null);
  }
}
