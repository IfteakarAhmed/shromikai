package bd.shromikai;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.logging.LogKeys;
import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * No log key outside the allowlist of architecture §15 (NFR-18, FR-03). Scans {@code src/main} for
 * MDC entries and SLF4J key-value pairs; keys must be allowlisted literals or {@link LogKeys}
 * constants. The runtime formatter drops unknown keys as a second line of defence.
 */
@Tag("unit")
@Requirement("NFR-18")
@Requirement("FR-03")
class LogAllowlistTest {

  private static final Pattern KEY_CALL =
      Pattern.compile("(?:MDC\\.put(?:Closeable)?|\\.addKeyValue)\\(\\s*([^,]+?)\\s*,");
  private static final Pattern LITERAL = Pattern.compile("\"([^\"]*)\"");
  private static final Pattern CONSTANT = Pattern.compile("LogKeys\\.([A-Z_]+)");

  @Test
  void shouldUseOnlyAllowlistedKeys_inMainSources() throws IOException {
    List<String> violations = new ArrayList<>();
    Path main = Path.of("src/main/java");
    try (Stream<Path> files = Files.walk(main)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        violations.addAll(violations(main.relativize(file).toString(), Files.readString(file)));
      }
    }
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldReportViolation_whenSourceLogsUnknownKey() {
    String source =
        """
        log.atInfo().addKeyValue("customerName", name).log("Saved");
        MDC.put(LogKeys.REQUEST_ID, id);
        MDC.put(dynamicKey, value);
        log.atWarn().addKeyValue(LogKeys.ANALYSIS_ID, id).log("Done");
        """;

    assertThat(violations("Sample.java", source))
        .containsExactly(
            "Sample.java: key \"customerName\" is not allowlisted",
            "Sample.java: key dynamicKey is not a literal or LogKeys constant");
  }

  @Test
  void shouldMatchTheArchitectureAllowlist() throws IOException {
    String architecture = Files.readString(Path.of("../docs/spec/architecture.md"));
    Matcher section =
        Pattern.compile("\\*\\*Log allowlist\\*\\*(.*?)\\n\\n", Pattern.DOTALL)
            .matcher(architecture);
    assertThat(section.find()).as("architecture §15 log allowlist").isTrue();
    Set<String> documented = new LinkedHashSet<>();
    Matcher key = Pattern.compile("`([a-zA-Z]+)`").matcher(section.group(1));
    while (key.find()) {
      if (!key.group(1).equals("LogAllowlistTest")) {
        documented.add(key.group(1));
      }
    }

    assertThat(LogKeys.ALL).containsExactlyInAnyOrderElementsOf(documented);
  }

  static List<String> violations(String file, String source) {
    Map<String, String> constants = constants();
    List<String> violations = new ArrayList<>();
    Matcher call = KEY_CALL.matcher(source);
    while (call.find()) {
      String argument = call.group(1);
      Matcher literal = LITERAL.matcher(argument);
      Matcher constant = CONSTANT.matcher(argument);
      if (literal.matches()) {
        if (!LogKeys.ALL.contains(literal.group(1))) {
          violations.add(file + ": key \"" + literal.group(1) + "\" is not allowlisted");
        }
      } else if (constant.matches()) {
        String value = constants.get(constant.group(1));
        if (value == null || !LogKeys.ALL.contains(value)) {
          violations.add(file + ": key " + argument + " is not allowlisted");
        }
      } else {
        violations.add(file + ": key " + argument + " is not a literal or LogKeys constant");
      }
    }
    return violations;
  }

  private static Map<String, String> constants() {
    Map<String, String> constants = new TreeMap<>();
    for (Field field : LogKeys.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
        try {
          constants.put(field.getName(), (String) field.get(null));
        } catch (IllegalAccessException e) {
          throw new IllegalStateException(e);
        }
      }
    }
    return constants;
  }
}
