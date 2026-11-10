package bd.shromikai.shared.ai.ollama;

import bd.shromikai.shared.config.ShromikaiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Health component {@code ollama} (FR-04): {@code UP} when Ollama answers and both local models
 * (embedding and local PII) are pulled; {@code DOWN} otherwise. Absent under {@code fake-models},
 * where nothing calls Ollama (architecture §16.3).
 */
@Component("ollama")
@Profile("!fake-models")
public final class OllamaHealthIndicator implements HealthIndicator {

  private final RestClient client;
  private final List<String> requiredModels;

  public OllamaHealthIndicator(ShromikaiProperties properties) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.ollama().healthTimeout()).build());
    factory.setReadTimeout(properties.ollama().healthTimeout());
    this.client =
        RestClient.builder().baseUrl(properties.ollama().baseUrl()).requestFactory(factory).build();
    this.requiredModels = List.of(properties.models().embedding(), properties.models().localPii());
  }

  @Override
  public Health health() {
    try {
      JsonNode version = client.get().uri("/api/version").retrieve().body(JsonNode.class);
      JsonNode tags = client.get().uri("/api/tags").retrieve().body(JsonNode.class);
      List<String> missing = missingModels(tags);
      Health.Builder builder = missing.isEmpty() ? Health.up() : Health.down();
      builder.withDetail("version", version == null ? "unknown" : version.path("version").asText());
      if (!missing.isEmpty()) {
        builder.withDetail("missingModels", missing);
      }
      return builder.build();
    } catch (RestClientException e) {
      return Health.down().withDetail("reason", "unreachable").build();
    }
  }

  private List<String> missingModels(JsonNode tags) {
    Set<String> present = new HashSet<>();
    if (tags != null) {
      tags.path("models")
          .forEach(
              model -> {
                String name = model.path("name").asText();
                present.add(name);
                if (name.endsWith(":latest")) {
                  present.add(name.substring(0, name.length() - ":latest".length()));
                }
              });
    }
    List<String> missing = new ArrayList<>();
    for (String model : requiredModels) {
      if (!present.contains(model)) {
        missing.add(model);
      }
    }
    return missing;
  }
}
