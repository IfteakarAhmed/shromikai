package bd.shromikai.privacy.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Loads {@code keywords.yml} into case-insensitive "keyword near here" patterns. */
public final class Keywords {

  /** How many characters before a number a keyword may sit. */
  public static final int NEAR = 40;

  private final Map<String, Pattern> patterns;

  private Keywords(Map<String, Pattern> patterns) {
    this.patterns = patterns;
  }

  public static Keywords load() {
    try (InputStream in = Keywords.class.getResourceAsStream("keywords.yml")) {
      if (in == null) {
        throw new IllegalStateException("keywords.yml is missing");
      }
      Map<String, List<String>> raw = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
      return new Keywords(
          raw.entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> compile(e.getValue()))));
    } catch (IOException e) {
      throw new IllegalStateException("keywords.yml cannot be read", e);
    }
  }

  /**
   * Latin keywords match whole words only. Bangla keywords match as prefixes of a word, because
   * Bangla attaches suffixes to the word (for example "পরিচয়পত্র").
   */
  private static Pattern compile(List<String> words) {
    String alternatives =
        words.stream()
            .map(
                w ->
                    w.codePointAt(0) < 128
                        ? "(?<![\\p{L}\\p{M}])" + Pattern.quote(w) + "(?![\\p{L}\\p{M}])"
                        : Pattern.quote(w))
            .collect(Collectors.joining("|", "(?:", ")"));
    return Pattern.compile(alternatives, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  /** {@code true} if a keyword of the group occurs within {@link #NEAR} characters before start. */
  public boolean near(String group, String text, int start) {
    Pattern pattern = patterns.get(group);
    if (pattern == null) {
      throw new IllegalArgumentException("unknown keyword group " + group);
    }
    int from = Math.max(0, start - NEAR);
    var matcher = pattern.matcher(text);
    matcher.region(from, start);
    matcher.useTransparentBounds(true);
    return matcher.find();
  }
}
