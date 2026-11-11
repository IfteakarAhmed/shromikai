package bd.shromikai.retrieval.internal;

import bd.shromikai.retrieval.QueryRewriter;
import bd.shromikai.shared.ai.ModelException;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.logging.LogKeys;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Query rewriting with the {@code query-rewrite} prompt (FR-21). Invalid output is repaired once
 * inside {@link ModelGateway}; any model failure, an abstention or an empty answer returns empty
 * and the caller uses the raw clause. Only redacted clauses may be passed in (ADR-0006).
 */
@Component
final class ModelGatewayQueryRewriter implements QueryRewriter {

  static final String PROMPT_ID = "query-rewrite";
  static final int MAX_QUERIES = 3;

  private static final Logger LOG = LoggerFactory.getLogger(ModelGatewayQueryRewriter.class);

  private final ModelGateway gateway;

  ModelGatewayQueryRewriter(ModelGateway gateway) {
    this.gateway = gateway;
  }

  @Override
  public Optional<List<String>> rewrite(String redactedClause) {
    JsonNode output;
    try {
      output = gateway.call(PROMPT_ID, Map.of("clause", redactedClause)).output();
    } catch (ModelException e) {
      LOG.atWarn()
          .addKeyValue(LogKeys.REASON_CODE, e.reasonCode())
          .log("query rewrite failed, using the raw clause");
      return Optional.empty();
    }
    if (!"OK".equals(output.path("status").asText())) {
      return Optional.empty();
    }
    List<String> queries = new ArrayList<>();
    for (JsonNode query : output.path("queries")) {
      String text = query.asText().strip();
      if (!text.isEmpty() && queries.size() < MAX_QUERIES) {
        queries.add(text);
      }
    }
    return queries.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(queries));
  }
}
