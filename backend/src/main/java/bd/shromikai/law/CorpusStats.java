package bd.shromikai.law;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Size of the statute corpus. {@code release} and {@code lastRefreshAt} stay {@code null} until
 * corpus releases exist (V4, P3).
 */
public record CorpusStats(
    @Nullable String release,
    long acts,
    long sections,
    long currentSections,
    long embeddedSections,
    @Nullable Instant lastRefreshAt) {}
