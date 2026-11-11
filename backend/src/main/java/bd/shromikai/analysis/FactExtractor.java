package bd.shromikai.analysis;

import bd.shromikai.rules.ClauseFact;
import bd.shromikai.rules.FactParameter;
import bd.shromikai.rules.Unit;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;

/**
 * Typed facts per clause (FR-35): {@code clause-facts} on a cheap model reads them, a regex
 * cross-check lowers a fact's confidence by 0.3 when it finds a different number. Facts with a unit
 * that does not belong to their parameter are dropped. Only redacted text is sent (R-03).
 */
@Component
public final class FactExtractor {

  static final String PROMPT = "clause-facts";
  static final int BATCHES = 3;

  /**
   * At most this many clauses per call. The schema does not bound the facts per clause (a clause
   * can state several), so a batch can still stop at the output cap; {@link #extractTolerant} then
   * splits it (ADR-0019).
   */
  static final int MAX_PER_BATCH = 4;

  static final double DISAGREEMENT_PENALTY = 0.3;

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  /** Facts of every clause in input order; a clause without facts maps to an empty list. */
  public Map<String, List<ClauseFact>> extract(
      ModelSession session, Stage stage, List<Clause> clauses) {
    List<List<Clause>> batches = batches(clauses);
    List<Future<Map<String, List<ClauseFact>>>> futures = new ArrayList<>();
    for (List<Clause> batch : batches) {
      futures.add(executor.submit(() -> extractTolerant(session, stage, batch)));
    }
    Map<String, List<ClauseFact>> out = new LinkedHashMap<>();
    try {
      for (Future<Map<String, List<ClauseFact>>> future : futures) {
        out.putAll(future.get());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      futures.forEach(f -> f.cancel(true));
      throw new IllegalStateException("fact extraction interrupted", e);
    } catch (ExecutionException e) {
      futures.forEach(f -> f.cancel(true));
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("fact extraction failed", e.getCause());
    }
    Map<String, List<ClauseFact>> ordered = new LinkedHashMap<>();
    for (Clause clause : clauses) {
      ordered.put(clause.id(), out.getOrDefault(clause.id(), List.of()));
    }
    return ordered;
  }

  /** Facts of one piece of text, for example a counter-offer proposal. */
  public List<ClauseFact> extractOne(ModelSession session, Stage stage, String id, String text) {
    return extractBatch(
            session,
            stage,
            List.of(
                new Clause(
                    id,
                    null,
                    List.of(),
                    bd.shromikai.shared.SensitiveText.of(text),
                    java.util.Set.of(),
                    ClauseType.OTHER,
                    1.0)))
        .getOrDefault(id, List.of());
  }

  /**
   * Reads a batch; if the answer stops at the output cap the batch is split in two and each half is
   * read again (the usage of the cut call is already counted). A single clause that still stops at
   * the cap yields no facts: its rules then cannot be evaluated, but the analysis goes on and the
   * risk agent still reads the clause. Other failures are thrown.
   */
  private Map<String, List<ClauseFact>> extractTolerant(
      ModelSession session, Stage stage, List<Clause> batch) {
    try {
      return extractBatch(session, stage, batch);
    } catch (ModelOutputInvalidException e) {
      if (!e.stoppedAtCap()) {
        throw e;
      }
      if (batch.size() < 2) {
        return Map.of();
      }
      int half = batch.size() / 2;
      Map<String, List<ClauseFact>> merged = new HashMap<>();
      merged.putAll(extractTolerant(session, stage, batch.subList(0, half)));
      merged.putAll(extractTolerant(session, stage, batch.subList(half, batch.size())));
      return merged;
    }
  }

  private Map<String, List<ClauseFact>> extractBatch(
      ModelSession session, Stage stage, List<Clause> batch) {
    List<Map<String, String>> input = new ArrayList<>();
    Map<String, String> texts = new HashMap<>();
    for (Clause clause : batch) {
      input.add(Map.of("id", clause.id(), "text", clause.text().reveal()));
      texts.put(clause.id(), clause.text().reveal());
    }
    ModelGateway.Result result = session.call(stage, PROMPT, Map.of("clauses", input));
    Map<String, List<ClauseFact>> out = new HashMap<>();
    for (JsonNode item : result.output().path("clauses")) {
      String id = item.path("id").asText();
      String text = texts.get(id);
      if (text == null) {
        continue;
      }
      List<RegexFacts.Found> found = RegexFacts.find(text);
      List<ClauseFact> facts = new ArrayList<>();
      for (JsonNode f : item.path("facts")) {
        ClauseFact fact = fact(f, found);
        if (fact != null) {
          facts.add(fact);
        }
      }
      out.put(id, facts);
    }
    return out;
  }

  private static ClauseFact fact(JsonNode f, List<RegexFacts.Found> found) {
    FactParameter parameter;
    Unit unit;
    try {
      parameter = FactParameter.valueOf(f.path("parameter").asText());
      unit = Unit.valueOf(f.path("unit").asText());
    } catch (IllegalArgumentException e) {
      return null;
    }
    if (!parameter.allowedUnits().contains(unit) || !f.path("value").isNumber()) {
      return null;
    }
    double value = f.path("value").asDouble();
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return null;
    }
    double confidence = Math.max(0, Math.min(1, f.path("confidence").asDouble(0)));
    ClauseFact stated = new ClauseFact(parameter, value, unit, confidence, null);
    Boolean agrees = RegexFacts.agrees(stated, found);
    if (Boolean.FALSE.equals(agrees)) {
      confidence = Math.max(0, confidence - DISAGREEMENT_PENALTY);
    }
    return new ClauseFact(parameter, value, unit, confidence, agrees);
  }

  /** Three contiguous batches of roughly equal size, or batches of {@link #MAX_PER_BATCH}. */
  static List<List<Clause>> batches(List<Clause> clauses) {
    List<List<Clause>> out = new ArrayList<>();
    if (clauses.isEmpty()) {
      return out;
    }
    int size = Math.min(MAX_PER_BATCH, (clauses.size() + BATCHES - 1) / BATCHES);
    for (int from = 0; from < clauses.size(); from += size) {
      out.add(clauses.subList(from, Math.min(clauses.size(), from + size)));
    }
    return out;
  }
}
