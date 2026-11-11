package bd.shromikai.law.internal;

import java.nio.file.Path;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code shromikai.scraper.*} (architecture §12). The contact comes from {@code SCRAPER_CONTACT}.
 */
@ConfigurationProperties("shromikai.scraper")
record ScraperProperties(
    String userAgent,
    @Nullable String contact,
    Duration minInterval,
    Duration timeout,
    Path rawDir,
    ScraperSettings.CachePolicy cachePolicy) {

  boolean hasContact() {
    return contact != null && !contact.isBlank();
  }
}
