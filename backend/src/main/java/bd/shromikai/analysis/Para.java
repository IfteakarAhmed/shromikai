package bd.shromikai.analysis;

import bd.shromikai.shared.SensitiveText;
import java.util.EnumSet;
import java.util.Set;

/** One redacted paragraph with the flags the earlier steps set. */
public record Para(String id, SensitiveText text, Set<ClauseFlag> flags) {

  public Para {
    flags = flags.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(flags));
  }
}
