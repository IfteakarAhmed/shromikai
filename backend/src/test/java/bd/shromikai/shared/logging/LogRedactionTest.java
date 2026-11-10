package bd.shromikai.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.KeyValuePair;

/** All sample values are invented; none belongs to a real person (test-strategy.md §13). */
@Tag("unit")
@Requirement("FR-03")
@Requirement("NFR-18")
class LogRedactionTest {

  private static final String NID = "1990123456789";
  private static final String MOBILE_LATIN = "01712345678";
  private static final String MOBILE_BANGLA = "০১৭১২৩৪৫৬৭৮";
  private static final String EMAIL = "rahim.fictional@example.com";

  private final ObjectMapper mapper = new ObjectMapper();
  private final AllowlistJsonFormatter formatter = new AllowlistJsonFormatter();
  private final Logger thirdParty =
      ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
          .getLogger("org.example.Library");

  @Test
  void shouldMaskNidMobileAndEmail_whenLogLineContainsThem() {
    String line = "worker " + NID + " phone " + MOBILE_LATIN + " mail " + EMAIL;

    String redacted = LogRedactor.redact(line);

    assertThat(redacted)
        .doesNotContain(NID)
        .doesNotContain(MOBILE_LATIN)
        .doesNotContain(EMAIL)
        .contains("[NID]", "[PHONE]", "[EMAIL]");
  }

  @Test
  void shouldMaskMobile_whenWrittenInBanglaDigits() {
    assertThat(LogRedactor.redact("ফোন " + MOBILE_BANGLA)).isEqualTo("ফোন [PHONE]");
  }

  @ParameterizedTest
  @ValueSource(strings = {"+8801712345678", "8801812345678", "01912-345678", "+৮৮০১৭১২৩৪৫৬৭৮"})
  void shouldMaskMobile_whenPrefixedOrSeparated(String mobile) {
    assertThat(LogRedactor.redact("call " + mobile + " now")).isEqualTo("call [PHONE] now");
  }

  /** Separators between digit groups and after +880, in Latin and Bangla digits (R-02). */
  static Stream<String> separatedMobiles() {
    List<String> latin =
        List.of(
            "+880 1712-345678",
            "+880-1712-345678",
            "+880 1712 345678",
            "0171-234-5678",
            "01712 345 678");
    return Stream.concat(latin.stream(), latin.stream().map(LogRedactionTest::banglaDigits));
  }

  static String banglaDigits(String latin) {
    StringBuilder out = new StringBuilder();
    latin
        .chars()
        .forEach(c -> out.append(Character.isDigit(c) ? (char) ('০' + (c - '0')) : (char) c));
    return out.toString();
  }

  @ParameterizedTest
  @MethodSource("separatedMobiles")
  void shouldMaskMobile_whenDigitGroupsAreSeparated(String mobile) {
    assertThat(LogRedactor.redact("call " + mobile + " now")).isEqualTo("call [PHONE] now");
    assertThat(LogRedactor.piiMatches("call " + mobile + " now")).containsExactly(mobile);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567890", "1990123456789", "19901234567890123", "১৯৯০১২৩৪৫৬৭৮৯"})
  void shouldMaskNid_whenItHas10Or13Or17Digits(String nid) {
    assertThat(LogRedactor.redact("nid " + nid)).isEqualTo("nid [NID]");
  }

  @Test
  void shouldKeepShortNumbers_whenTheyAreNotIdentifiers() {
    assertThat(LogRedactor.redact("analysis took 2500 ms for 12 pages"))
        .isEqualTo("analysis took 2500 ms for 12 pages");
  }

  @Test
  void shouldReplaceFreeText_whenLongerThan200Characters() {
    String clause = "শ্রমিক ".repeat(40);

    assertThat(LogRedactor.redact(clause))
        .isEqualTo("[REDACTED_TEXT length=" + clause.length() + "]");
  }

  @Test
  void shouldMaskFormattedArguments_whenThirdPartyLogsPii() throws Exception {
    LoggingEvent event =
        new LoggingEvent(
            "fqcn",
            thirdParty,
            Level.INFO,
            "user {} at {}",
            null,
            new Object[] {EMAIL, MOBILE_LATIN});

    JsonNode json = mapper.readTree(formatter.format(event));

    assertThat(json.get("message").asText()).isEqualTo("user [EMAIL] at [PHONE]");
  }

  @Test
  void shouldEmitTemplateOnly_whenProjectLoggerFormatsArguments() throws Exception {
    Logger project =
        ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
            .getLogger("bd.shromikai.analysis.Example");
    LoggingEvent event =
        new LoggingEvent("fqcn", project, Level.INFO, "clause {}", null, new Object[] {"anything"});

    JsonNode json = mapper.readTree(formatter.format(event));

    assertThat(json.get("message").asText()).isEqualTo("clause {}");
  }

  @Test
  void shouldMaskKeyValues_andDropUnknownKeys() throws Exception {
    LoggingEvent event = new LoggingEvent("fqcn", thirdParty, Level.WARN, "x", null, null);
    event.setMDCPropertyMap(Map.of("requestId", "r-1", "contractText", "secret"));
    event.setKeyValuePairs(
        List.of(new KeyValuePair("errorCode", "nid " + NID), new KeyValuePair("worker", "x")));

    JsonNode json = mapper.readTree(formatter.format(event));

    assertThat(json.get("requestId").asText()).isEqualTo("r-1");
    assertThat(json.get("errorCode").asText()).isEqualTo("nid [NID]");
    assertThat(json.has("contractText")).isFalse();
    assertThat(json.has("worker")).isFalse();
    assertThat(json.get("timestamp").asText())
        .matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z");
    assertThat(json.get("level").asText()).isEqualTo("WARN");
  }

  @Test
  void shouldReduceThrowableToClassName_whenEventCarriesException() throws Exception {
    LoggingEvent event =
        new LoggingEvent(
            "fqcn",
            thirdParty,
            Level.ERROR,
            "failed",
            new IllegalStateException("clause text " + EMAIL),
            null);

    String line = formatter.format(event);

    assertThat(line).doesNotContain("clause text").doesNotContain(EMAIL).doesNotContain("at bd.");
    assertThat(mapper.readTree(line).get("reasonCode").asText()).isEqualTo("IllegalStateException");
  }
}
