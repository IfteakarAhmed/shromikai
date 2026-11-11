package bd.shromikai.law.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ScraperProperties.class, IngestProperties.class})
class LawConfiguration {

  LawConfiguration(ScraperProperties scraper, Environment environment) {
    // At most one request per second against a real source (FR-12); only tests go faster.
    if (!environment.acceptsProfiles(Profiles.of("test"))
        && scraper.minInterval().compareTo(Duration.ofSeconds(1)) < 0) {
      throw new IllegalStateException("shromikai.scraper.min-interval must be at least 1s");
    }
  }
}
