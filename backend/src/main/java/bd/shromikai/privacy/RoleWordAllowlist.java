package bd.shromikai.privacy;

import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.text.BanglaNormalizer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Generic role, title and signing words (for example "মালিক", "কর্মচারী", "স্বাক্ষর") that the
 * local model sometimes returns as names, loaded from {@code data/privacy/role-words.yaml}
 * (ADR-0025).
 *
 * <p>The list is applied to the strings the local model returned, as a whole-value match only: an
 * entity string that equals a listed word (after NFC, trimming of edge punctuation and case
 * folding) is dropped. A longer value that merely contains a listed word is kept, so "মালিক রহিম
 * নমুনা সরকার" is still redacted. It never touches the deterministic detectors and the egress guard
 * does not read it: the guard still blocks every original value that was redacted.
 *
 * <p>Why this keeps R-03: a dropped word is a generic word, not an identifier. Redacting it gains
 * no privacy, and in Bangla it caused a failure, because an inflected form ("মালিকের") cannot be
 * replaced by the whole-word matcher yet still contains the protected value, so the guard blocked
 * the whole analysis.
 */
@Component
public final class RoleWordAllowlist {

  private static final Pattern EDGE = Pattern.compile("^[\\p{P}\\p{S}\\s]+|[\\p{P}\\p{S}\\s]+$");
  private static final Pattern FORBIDDEN_IN_ENTRY = Pattern.compile("[\\p{N}@/\\\\:]");
  private static final Path DEFAULT_FILE = Path.of("..", "data", "privacy", "role-words.yaml");

  private final Set<String> words;

  @Autowired
  RoleWordAllowlist(ShromikaiProperties properties) {
    this(Path.of(properties.privacy().roleWordsFile()));
  }

  RoleWordAllowlist(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      this.words = parse(in, file.toString());
    } catch (IOException e) {
      throw new IllegalStateException("role-word file cannot be read: " + file, e);
    }
  }

  /** For tests that read an in-memory file. */
  RoleWordAllowlist(InputStream yaml) {
    this.words = parse(yaml, "role words");
  }

  private RoleWordAllowlist() {
    this.words = Set.of();
  }

  /** An empty list: nothing is dropped (the behaviour before ADR-0025). */
  public static RoleWordAllowlist none() {
    return new RoleWordAllowlist();
  }

  /**
   * The list in the repository's {@code data/privacy}, for tests that run from {@code backend/}.
   */
  public static RoleWordAllowlist bundled() {
    return new RoleWordAllowlist(DEFAULT_FILE);
  }

  /** {@code true} if the whole value is one listed role word. */
  public boolean isRoleWord(String value) {
    if (value == null) {
      return false;
    }
    String key = key(value);
    return !key.isEmpty() && words.contains(key);
  }

  /** The local model's entities without the whole-value role words. */
  public LocalEntities withoutRoleWords(LocalEntities entities) {
    return new LocalEntities(
        keep(entities.persons()),
        keep(entities.organisations()),
        keep(entities.addresses()),
        keep(entities.employeeIds()));
  }

  public int size() {
    return words.size();
  }

  private List<String> keep(List<String> values) {
    return values.stream().filter(v -> !isRoleWord(v)).toList();
  }

  private static String key(String value) {
    return BanglaNormalizer.matchingForm(EDGE.matcher(value.strip()).replaceAll(""));
  }

  private static Set<String> parse(InputStream in, String origin) {
    Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    if (!(loaded instanceof Map<?, ?> root) || !(root.get("words") instanceof Map<?, ?> byLang)) {
      throw new IllegalStateException(origin + " needs a 'words' mapping");
    }
    Set<String> out = new HashSet<>();
    for (Map.Entry<?, ?> lang : byLang.entrySet()) {
      if (!(lang.getValue() instanceof List<?> list)) {
        throw new IllegalStateException(origin + ": words." + lang.getKey() + " must be a list");
      }
      List<String> entries = new ArrayList<>();
      for (Object entry : list) {
        String word = entry == null ? "" : String.valueOf(entry);
        if (key(word).isEmpty() || FORBIDDEN_IN_ENTRY.matcher(word).find()) {
          throw new IllegalStateException(
              origin
                  + ": words."
                  + lang.getKey()
                  + " has an entry that is empty or looks like an"
                  + " identifier (digits, @, / or :)");
        }
        entries.add(key(word));
      }
      out.addAll(entries);
    }
    if (out.isEmpty()) {
      throw new IllegalStateException(origin + " lists no words");
    }
    return Set.copyOf(out);
  }
}
