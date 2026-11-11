package bd.shromikai.law;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Size of the statute corpus. {@code release} and {@code lastRefreshAt} come from the latest corpus
 * release and stay {@code null} until the first ingestion has run (V4, P3).
 */
public record CorpusStats(
    @Nullable String release,
    long acts,
    long sections,
    long currentSections,
    long embeddedSections,
    @Nullable Instant lastRefreshAt) {}
