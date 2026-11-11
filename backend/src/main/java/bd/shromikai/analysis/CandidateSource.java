package bd.shromikai.analysis;

import bd.shromikai.retrieval.Retrieval;
import bd.shromikai.shared.ai.EgressContext;
import org.jspecify.annotations.Nullable;

/** Retrieval as the analysis uses it: a redacted query in, ranked candidate sections out. */
public interface CandidateSource {

  Retrieval search(String redactedQuery, @Nullable String clauseType, EgressContext egress);
}
