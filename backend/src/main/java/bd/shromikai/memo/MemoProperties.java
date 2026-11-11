package bd.shromikai.memo;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Renderer settings ({@code shromikai.memo.*}): the Gotenberg address and its time limits. */
@ConfigurationProperties("shromikai.memo")
public record MemoProperties(
    @Nullable String gotenbergUrl,
    @Nullable Duration renderTimeout,
    @Nullable Duration retryBackoff) {

  public MemoProperties {
    if (gotenbergUrl == null || gotenbergUrl.isBlank()) {
      gotenbergUrl = "http://localhost:3000";
    }
    if (renderTimeout == null) {
      renderTimeout = Duration.ofSeconds(30);
    }
    if (retryBackoff == null) {
      retryBackoff = Duration.ofMillis(500);
    }
  }
}
