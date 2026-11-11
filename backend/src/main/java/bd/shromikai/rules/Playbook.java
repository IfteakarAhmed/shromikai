package bd.shromikai.rules;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A stored playbook of one organisation (api.md §3 {@code Playbook}). */
public record Playbook(
    UUID id,
    UUID orgId,
    String name,
    String side,
    int version,
    Instant createdAt,
    @Nullable Instant archivedAt,
    List<PlaybookRule> rules) {

  public Playbook {
    rules = List.copyOf(rules);
  }
}
