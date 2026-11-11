package bd.shromikai.testsupport.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * The cumulative cap on live API spend (ADR-0015 decision 4): a development-time guard, not product
 * behaviour. The state lives in the git-ignored {@code .live-spend/ledger.json}, so it survives
 * test runs. A call is refused before it is made when its worst-case cost would pass the cap, and
 * every finished call is added at its reported usage. The cap is US$3.00: the team's API balance is
 * small and a live call cannot be undone.
 */
public final class LiveSpendLedger {

  /** US$3.00 in micro-dollars. */
  public static final long CAP_MICROS = 3_000_000;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private final Path file;
  private final long cap;

  public LiveSpendLedger() {
    this(Path.of("..", ".live-spend", "ledger.json"), CAP_MICROS);
  }

  public LiveSpendLedger(Path file, long capMicros) {
    this.file = file;
    this.cap = capMicros;
  }

  /** Refused: the next call could take total spend over the cap. */
  public static final class SpendCapException extends RuntimeException {
    public SpendCapException(String message) {
      super(message);
    }
  }

  public synchronized long spentMicros() {
    return load().path("spentMicros").asLong(0);
  }

  public long capMicros() {
    return cap;
  }

  public synchronized long remainingMicros() {
    return cap - spentMicros();
  }

  /** Throws unless {@code worstCaseMicros} still fits under the cap. */
  public synchronized void reserve(String what, long worstCaseMicros) {
    long spent = spentMicros();
    if (spent + worstCaseMicros > cap) {
      throw new SpendCapException(
          "Live spend cap US$"
              + cap / 1_000_000.0
              + " would be exceeded by "
              + what
              + ": spent US$"
              + spent / 1_000_000.0
              + ", this call could cost up to US$"
              + worstCaseMicros / 1_000_000.0
              + ". Ask the owner before spending more.");
    }
  }

  /** Adds a finished call. */
  public synchronized void record(
      String purpose, String modelId, long tokensIn, long tokensOut, long costMicros) {
    ObjectNode state = load();
    ArrayNode calls = state.withArray("calls");
    calls
        .addObject()
        .put("at", Instant.now().toString())
        .put("purpose", purpose)
        .put("modelId", modelId)
        .put("tokensIn", tokensIn)
        .put("tokensOut", tokensOut)
        .put("costMicros", costMicros);
    state.put("capMicros", cap);
    state.put("spentMicros", state.path("spentMicros").asLong(0) + costMicros);
    save(state);
  }

  /** Spend recorded for one purpose (for example one analysis), in micro-dollars. */
  public synchronized long spentOn(String purposePrefix) {
    long sum = 0;
    for (var call : load().withArray("calls")) {
      if (call.path("purpose").asText().startsWith(purposePrefix)) {
        sum += call.path("costMicros").asLong();
      }
    }
    return sum;
  }

  public synchronized int callsFor(String purposePrefix) {
    int n = 0;
    for (var call : load().withArray("calls")) {
      if (call.path("purpose").asText().startsWith(purposePrefix)) {
        n++;
      }
    }
    return n;
  }

  private ObjectNode load() {
    try {
      if (Files.isRegularFile(file)) {
        return (ObjectNode) MAPPER.readTree(file.toFile());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    ObjectNode fresh = MAPPER.createObjectNode();
    fresh.put("capMicros", cap).put("spentMicros", 0);
    fresh.putArray("calls");
    return fresh;
  }

  private void save(ObjectNode state) {
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(tmp, MAPPER.writeValueAsString(state), StandardCharsets.UTF_8);
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
