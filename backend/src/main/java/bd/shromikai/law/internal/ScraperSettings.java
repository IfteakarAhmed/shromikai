package bd.shromikai.law.internal;

import java.nio.file.Path;
import java.time.Duration;

/**
 * How the scraper behaves (FR-12). {@code userAgent} already contains the contact. {@code
 * cachePolicy} {@code PREFER_CACHE} reads a cached page instead of fetching it again (development
 * only); {@code REFRESH} always fetches.
 */
record ScraperSettings(
    String userAgent,
    Duration minInterval,
    Duration timeout,
    int maxRetries,
    Duration backoffBase,
    Path rawDir,
    CachePolicy cachePolicy) {

  enum CachePolicy {
    REFRESH,
    PREFER_CACHE
  }

  ScraperSettings {
    if (userAgent.isBlank()) {
      throw new IllegalArgumentException("user agent must not be blank");
    }
  }
}
