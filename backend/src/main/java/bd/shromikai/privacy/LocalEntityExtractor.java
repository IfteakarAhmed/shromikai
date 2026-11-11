package bd.shromikai.privacy;

import bd.shromikai.shared.ai.LocalModelUnavailableException;
import bd.shromikai.shared.ai.ModelException;
import bd.shromikai.shared.ai.ModelGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/**
 * Local named-entity extraction with {@code pii-extract} on qwen2.5:7b-instruct (FR-29, FR-31,
 * architecture §10.3). Chunks of at most 1,500 characters overlap by 200; four run in parallel.
 * Each chunk gets two attempts. A second failure throws {@link LocalModelUnavailableException}, so
 * the analysis stops before any cloud call (R-03).
 */
@Component
public final class LocalEntityExtractor {

  static final int CHUNK = 1500;
  static final int OVERLAP = 200;
  static final int PARALLEL = 4;
  static final String PROMPT = "pii-extract";

  private final ModelGateway gateway;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public LocalEntityExtractor(ModelGateway gateway) {
    this.gateway = gateway;
  }

  public LocalEntities extract(String text) {
    List<String> chunks = chunks(text);
    Semaphore permits = new Semaphore(PARALLEL);
    List<Future<LocalEntities>> futures = new ArrayList<>();
    for (String chunk : chunks) {
      futures.add(
          executor.submit(
              () -> {
                permits.acquire();
                try {
                  return extractChunk(chunk);
                } finally {
                  permits.release();
                }
              }));
    }
    LocalEntities all = LocalEntities.NONE;
    for (Future<LocalEntities> future : futures) {
      try {
        all = all.merge(future.get());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        futures.forEach(f -> f.cancel(true));
        throw new LocalModelUnavailableException("pii-extract-interrupted");
      } catch (ExecutionException e) {
        futures.forEach(f -> f.cancel(true));
        if (e.getCause() instanceof LocalModelUnavailableException unavailable) {
          throw unavailable;
        }
        throw new LocalModelUnavailableException("pii-extract-failed");
      }
    }
    return all;
  }

  private LocalEntities extractChunk(String chunk) {
    for (int attempt = 1; ; attempt++) {
      try {
        JsonNode out = gateway.call(PROMPT, Map.of("text", chunk)).output();
        return "OK".equals(out.path("status").asText())
            ? new LocalEntities(
                strings(out, "persons"),
                strings(out, "organisations"),
                strings(out, "addresses"),
                strings(out, "employee_ids"))
            : LocalEntities.NONE;
      } catch (ModelException | IllegalStateException e) {
        if (attempt >= 2) {
          throw new LocalModelUnavailableException("pii-extract-failed");
        }
      }
    }
  }

  private static List<String> strings(JsonNode node, String field) {
    List<String> out = new ArrayList<>();
    node.path(field).forEach(v -> out.add(v.asText()));
    return out;
  }

  /** Chunks of at most {@link #CHUNK} characters overlapping by {@link #OVERLAP}. */
  static List<String> chunks(String text) {
    List<String> out = new ArrayList<>();
    if (text.isBlank()) {
      return out;
    }
    int step = CHUNK - OVERLAP;
    for (int start = 0; start < text.length(); start += step) {
      int end = Math.min(text.length(), start + CHUNK);
      out.add(text.substring(start, end));
      if (end == text.length()) {
        break;
      }
    }
    return out;
  }
}
