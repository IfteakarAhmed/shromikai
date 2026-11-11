package bd.shromikai.analysis;

import bd.shromikai.shared.SensitiveText;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A clause of the redacted contract. {@code type} and {@code confidence} are set by the classifier.
 * The text is redacted and held as {@link SensitiveText} (R-02).
 *
 * @param label the clause's own number, such as {@code ১.} or {@code 2.}; {@code null} for the
 *     opening text before the first numbered clause
 */
public record Clause(
    String id,
    @Nullable String label,
    List<String> paragraphIds,
    SensitiveText text,
    Set<ClauseFlag> flags,
    @Nullable ClauseType type,
    double confidence) {

  public Clause {
    paragraphIds = List.copyOf(paragraphIds);
    flags = Set.copyOf(flags);
  }

  public Clause classified(ClauseType newType, double newConfidence) {
    return new Clause(id, label, paragraphIds, text, flags, newType, newConfidence);
  }
}
