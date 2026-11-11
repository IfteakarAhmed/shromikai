package bd.shromikai.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An API key as an admin may see it: never the key and never its hash. */
public record ApiKeyView(
    UUID id,
    UUID orgId,
    String prefix,
    String name,
    List<String> scopes,
    Instant createdAt,
    @Nullable Instant lastUsedAt,
    @Nullable Instant revokedAt) {}
