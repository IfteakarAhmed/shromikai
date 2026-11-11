package bd.shromikai.analysis;

import bd.shromikai.retrieval.Retrieval;
import bd.shromikai.retrieval.RetrievalService;
import bd.shromikai.retrieval.SearchOptions;
import bd.shromikai.shared.ai.EgressContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** {@link CandidateSource} over hybrid retrieval with query rewriting (architecture §8.2). */
@Component
final class RetrievalCandidateSource implements CandidateSource {

  private final RetrievalService retrieval;

  RetrievalCandidateSource(RetrievalService retrieval) {
    this.retrieval = retrieval;
  }

  @Override
  public Retrieval search(String redactedQuery, @Nullable String clauseType, EgressContext egress) {
    return retrieval.search(
        redactedQuery, SearchOptions.forClause(clauseType, SearchOptions.BD), egress);
  }
}
