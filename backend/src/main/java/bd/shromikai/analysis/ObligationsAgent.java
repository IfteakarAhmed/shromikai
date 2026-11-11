package bd.shromikai.analysis;

import bd.shromikai.analysis.CandidateSet.Candidate;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.Verdict;
import bd.shromikai.verify.VerificationContext;
import bd.shromikai.verify.VerifiedCitation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The obligations checklist (FR-53): one {@code obligations} call for the whole contract in the
 * {@code RISK} stage, with claimed citations restricted to the analysis's candidate keys, and a
 * verification in {@code VERIFY}. A statutory item whose citations all fail verification is dropped
 * (R-01: no unverified citation, and no statutory claim without one).
 */
@Component
public final class ObligationsAgent {

  static final String PROMPT = "obligations";
  static final String ENUM_PATH = "$.items[*].citations[*].section_key";
  static final int MAX_CANDIDATES = 20;

  private final CitationVerifier verifier;

  public ObligationsAgent(CitationVerifier verifier) {
    this.verifier = verifier;
  }

  /** Items with claimed citations, in the model's order. */
  public List<ObligationItem> propose(
      ModelSession session,
      AnalysisRequest request,
      List<RiskAgent.ClauseView> clauses,
      CandidateSet candidates) {
    List<Candidate> sections = candidates.all().stream().limit(MAX_CANDIDATES).toList();
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("output_language", request.outputLanguage());
    variables.put("candidate_sections", sections);
    variables.put("clauses", RiskAgent.clauseJson(clauses));
    List<String> keys = sections.stream().map(Candidate::sectionKey).toList();
    ModelGateway.Result result =
        session.call(Stage.RISK, PROMPT, variables, Map.of(ENUM_PATH, keys));
    return parse(
        result.output(),
        clauses.stream().map(v -> v.clause().id()).toList(),
        request.outputLanguage());
  }

  static List<ObligationItem> parse(JsonNode output, List<String> clauseIds, String language) {
    List<ObligationItem> out = new ArrayList<>();
    if (!"OK".equals(output.path("status").asText())) {
      return out;
    }
    for (JsonNode item : output.path("items")) {
      List<String> ids = new ArrayList<>();
      for (JsonNode id : item.path("clause_ids")) {
        if (clauseIds.contains(id.asText()) && !ids.contains(id.asText())) {
          ids.add(id.asText());
        }
      }
      List<ClaimedCitation> claims = new ArrayList<>();
      for (JsonNode c : item.path("citations")) {
        claims.add(
            ClaimedCitation.llm(
                c.path("section_key").asText(),
                c.path("quote").asText(),
                SensitiveText.of(c.path("claim").asText()),
                language));
      }
      JsonNode deadline = item.path("deadline");
      JsonNode trigger = item.path("trigger");
      out.add(
          new ObligationItem(
              "o" + (out.size() + 1),
              item.path("party").asText(),
              SensitiveText.of(item.path("obligation").asText()),
              trigger.isTextual() ? SensitiveText.of(trigger.asText()) : null,
              deadline.isObject()
                  ? new ObligationItem.Deadline(
                      deadline.path("value").asDouble(), deadline.path("unit").asText())
                  : null,
              item.path("source").asText(),
              item.path("status").asText(),
              ids,
              claims,
              List.of()));
    }
    return out;
  }

  /**
   * Verifies the claimed citations. A contract-only item is kept as it is; any other item keeps its
   * verified citations, or is dropped when it has none.
   */
  public List<ObligationItem> verify(List<ObligationItem> items, VerificationContext context) {
    List<ClaimedCitation> claims = new ArrayList<>();
    items.forEach(i -> claims.addAll(i.claims()));
    Map<UUID, Verdict> verdicts =
        verifier.verifyAll(claims, context).stream()
            .collect(Collectors.toMap(v -> v.result().citationId(), Function.identity()));
    List<ObligationItem> out = new ArrayList<>();
    for (ObligationItem item : items) {
      List<VerifiedCitation> verified = new ArrayList<>();
      for (ClaimedCitation claim : item.claims()) {
        Verdict verdict = verdicts.get(claim.citationId());
        if (verdict != null && verdict.verified()) {
          verified.add(verdict.citation());
        }
      }
      if (item.needsCitation() && verified.isEmpty()) {
        continue;
      }
      out.add(item.verified(verified));
    }
    return renumber(out);
  }

  private static List<ObligationItem> renumber(List<ObligationItem> items) {
    List<ObligationItem> out = new ArrayList<>();
    for (ObligationItem item : items) {
      out.add(
          new ObligationItem(
              "o" + (out.size() + 1),
              item.party(),
              item.obligation(),
              item.trigger(),
              item.deadline(),
              item.source(),
              item.status(),
              item.clauseIds(),
              item.claims(),
              item.citations()));
    }
    return out;
  }

  /** For tests and callers that need the nullable deadline unit. */
  static @Nullable String unitOf(ObligationItem item) {
    return item.deadline() == null ? null : item.deadline().unit();
  }
}
