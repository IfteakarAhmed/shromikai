package bd.shromikai.law;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stable citation identifier {@code {actCode}:{sectionNo}@{versionLabel}} (AGENTS.md §2, api.md
 * §5.4). Section numbers are in Latin digits.
 */
public record SectionKey(String actCode, String sectionNo, String versionLabel) {

  public static final Pattern PATTERN =
      Pattern.compile("^([A-Za-z][A-Za-z0-9-]{1,31}):([0-9A-Za-z().-]{1,32})@([a-z0-9-]{1,32})$");

  public SectionKey {
    if (!PATTERN.matcher(actCode + ":" + sectionNo + "@" + versionLabel).matches()) {
      throw new IllegalArgumentException(
          "Not a section key: " + actCode + ":" + sectionNo + "@" + versionLabel);
    }
  }

  public static Optional<SectionKey> parse(String text) {
    Matcher matcher = PATTERN.matcher(text);
    return matcher.matches()
        ? Optional.of(new SectionKey(matcher.group(1), matcher.group(2), matcher.group(3)))
        : Optional.empty();
  }

  @Override
  public String toString() {
    return actCode + ":" + sectionNo + "@" + versionLabel;
  }
}
