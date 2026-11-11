package bd.shromikai.analysis;

import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Assigns a {@link ClauseType} with confidence to each clause and gates the document type (FR-33):
 * two batches run in parallel through {@code clause-classify}; the first batch (which holds the
 * opening of the document) decides whether this is an employment contract. Anything else fails with
 * {@code not-an-employment-contract}. Clause text is sent redacted only, and the egress guard
 * checks every request (R-03).
 */
@Component
public final class ClauseClassifier {

  static final String PROMPT = "clause-classify";
  static final int BATCHES = 2;

  /** The classified clauses and what the model recorded about the call. */
  public record Result(List<Clause> clauses, String promptId, int promptVersion, String modelId) {}

  private final ModelGateway gateway;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public ClauseClassifier(ModelGateway gateway) {
    this.gateway = gateway;
  }

  public Result classify(List<Clause> clauses, @Nullable EgressContext context) {
    List<List<Clause>> batches = batches(clauses);
    List<Future<ModelGateway.Result>> futures = new ArrayList<>();
    for (List<Clause> batch : batches) {
      List<Map<String, String>> input = new ArrayList<>();
      for (Clause clause : batch) {
        input.add(Map.of("id", clause.id(), "text", clause.text().reveal()));
      }
      futures.add(executor.submit(() -> gateway.call(PROMPT, Map.of("clauses", input), context)));
    }
    List<ModelGateway.Result> results = new ArrayList<>();
    try {
      for (Future<ModelGateway.Result> future : futures) {
        results.add(future.get());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      futures.forEach(f -> f.cancel(true));
      throw new ProblemException(ProblemType.INTERNAL_ERROR, "Classification was interrupted.");
    } catch (ExecutionException e) {
      futures.forEach(f -> f.cancel(true));
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new ProblemException(ProblemType.INTERNAL_ERROR, "Classification failed.");
    }
    JsonNode gate = results.getFirst().output();
    if ("ABSTAIN".equals(gate.path("status").asText())) {
      throw new ProblemException(ProblemType.UNREADABLE_DOCUMENT, "The text could not be read.");
    }
    if (!"EMPLOYMENT_CONTRACT".equals(gate.path("document_type").asText())) {
      throw new ProblemException(ProblemType.NOT_AN_EMPLOYMENT_CONTRACT);
    }
    Map<String, JsonNode> byId = new HashMap<>();
    for (ModelGateway.Result result : results) {
      for (JsonNode item : result.output().path("clauses")) {
        byId.put(item.path("id").asText(), item);
      }
    }
    List<Clause> out = new ArrayList<>();
    for (Clause clause : clauses) {
      JsonNode item = byId.get(clause.id());
      if (item == null) {
        out.add(clause.classified(ClauseType.OTHER, 0));
        continue;
      }
      double confidence = Math.max(0, Math.min(1, item.path("confidence").asDouble(0)));
      out.add(clause.classified(ClauseType.valueOf(item.path("type").asText()), confidence));
    }
    ModelGateway.Result first = results.getFirst();
    return new Result(out, first.promptId(), first.promptVersion(), first.modelId());
  }

  /** Two roughly equal batches in document order (one if there are fewer than two clauses). */
  static List<List<Clause>> batches(List<Clause> clauses) {
    List<List<Clause>> out = new ArrayList<>();
    int size = (clauses.size() + BATCHES - 1) / BATCHES;
    for (int from = 0; from < clauses.size(); from += Math.max(1, size)) {
      out.add(clauses.subList(from, Math.min(clauses.size(), from + Math.max(1, size))));
    }
    return out;
  }
}
