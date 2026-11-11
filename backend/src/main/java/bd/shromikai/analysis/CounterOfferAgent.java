package bd.shromikai.analysis;

import bd.shromikai.rules.ClauseFact;
import bd.shromikai.rules.Rule;
import bd.shromikai.rules.RuleComparator;
import bd.shromikai.rules.RuleEngine;
import bd.shromikai.rules.RuleViolation;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.ModelRefusedException;
import bd.shromikai.shared.ai.ModelRequestRejectedException;
import bd.shromikai.shared.ai.ModelTimeoutException;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.verify.VerifiedCitation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The counter-offer agent (FR-41): after verification, one call for a {@code VERIFIED} finding of
 * kind {@code CONFLICT}, {@code AMBIGUITY} or {@code OMISSION}. The proposal's facts are re-read by
 * {@code clause-facts} and checked by the rule engine; a violation triggers one regeneration, a
 * second drops the proposal (warning {@code counter-offer-dropped}). An {@code UNCERTAIN} finding
 * never reaches this class, so it gets no counter-offer call.
 */
@Component
public final class CounterOfferAgent {

  static final String PROMPT = "counter-offer";

  /**
   * The redline, or {@code null} with the reason it was dropped. {@code unchecked} is true when a
   * redline was kept although no active rule could check its facts (warning {@code
   * counter-offer-unchecked}).
   */
  public record Outcome(
      @Nullable Redline redline, @Nullable String droppedBecause, boolean unchecked) {

    public Outcome(@Nullable Redline redline, @Nullable String droppedBecause) {
      this(redline, droppedBecause, false);
    }
  }

  private final FactExtractor facts;
  private final RuleEngine engine;

  public CounterOfferAgent(FactExtractor facts, RuleEngine engine) {
    this.facts = facts;
    this.engine = engine;
  }

  /** Whether a finding is eligible for a counter-offer at all (FR-41). */
  public static boolean eligible(Finding finding) {
    return finding.verification() == FindingVerification.VERIFIED
        && (finding.kind() == FindingKind.CONFLICT
            || finding.kind() == FindingKind.AMBIGUITY
            || finding.kind() == FindingKind.OMISSION);
  }

  /** A finding's outcome. */
  public record ForFinding(String findingId, Outcome outcome) {}

  /**
   * One counter-offer per eligible finding, up to eight in parallel; redline IDs {@code r1}, {@code
   * r2} follow the order of the findings. A finding that is not {@code VERIFIED} (or whose kind
   * takes no redline) gets no call at all.
   */
  public List<ForFinding> proposeAll(
      ModelSession session,
      AnalysisRequest request,
      List<Finding> findings,
      Map<String, Clause> clauses,
      List<Rule> activeRules) {
    java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(8);
    List<java.util.concurrent.Future<ForFinding>> futures = new ArrayList<>();
    int n = 0;
    try {
      for (Finding finding : findings) {
        if (!eligible(finding)) {
          continue;
        }
        String redlineId = "r" + (++n);
        Clause clause =
            finding.clauseIds().isEmpty() ? null : clauses.get(finding.clauseIds().getFirst());
        futures.add(
            executor.submit(
                () -> {
                  slots.acquire();
                  try {
                    return new ForFinding(
                        finding.id(),
                        propose(session, request, finding, clause, activeRules, redlineId));
                  } finally {
                    slots.release();
                  }
                }));
      }
      List<ForFinding> out = new ArrayList<>();
      for (java.util.concurrent.Future<ForFinding> future : futures) {
        out.add(future.get());
      }
      return out;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      futures.forEach(f -> f.cancel(true));
      throw new IllegalStateException("counter-offers interrupted", e);
    } catch (java.util.concurrent.ExecutionException e) {
      futures.forEach(f -> f.cancel(true));
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("counter-offers failed", e.getCause());
    } finally {
      executor.shutdown();
    }
  }

  public Outcome propose(
      ModelSession session,
      AnalysisRequest request,
      Finding finding,
      @Nullable Clause clause,
      List<Rule> activeRules,
      String redlineId) {
    if (!eligible(finding)) {
      throw new IllegalArgumentException("No counter-offer for finding " + finding.id());
    }
    List<Rule> checkable =
        activeRules.stream().filter(r -> r.comparator() != RuleComparator.REQUIRED).toList();
    String problem = "none";
    for (int attempt = 1; attempt <= 2; attempt++) {
      JsonNode answer;
      try {
        ModelGateway.Result result =
            session.call(
                Stage.COMPOSE, PROMPT, variables(request, finding, clause, activeRules, problem));
        answer = result.output();
      } catch (ModelOutputInvalidException | ModelRefusedException e) {
        return new Outcome(null, "model-output");
      } catch (ModelTimeoutException
          | UpstreamUnavailableException
          | ModelRequestRejectedException e) {
        // A redline is optional: the finding stays, the warning says it was dropped.
        return new Outcome(null, "model-unavailable");
      }
      if (!"OK".equals(answer.path("status").asText())
          || answer.path("proposed_text").asText().isBlank()) {
        return new Outcome(null, "abstained");
      }
      String proposed = answer.path("proposed_text").asText();
      List<String> violated = List.of();
      if (!checkable.isEmpty()) {
        List<ClauseFact> proposalFacts;
        try {
          proposalFacts = facts.extractOne(session, Stage.COMPOSE, "proposal", proposed);
        } catch (ModelOutputInvalidException
            | ModelRefusedException
            | ModelTimeoutException
            | UpstreamUnavailableException
            | ModelRequestRejectedException e) {
          return new Outcome(null, "model-unavailable");
        }
        violated =
            engine
                .evaluate(
                    List.of(new RuleEngine.ClauseFacts("proposal", proposalFacts)), checkable, null)
                .violations()
                .stream()
                .filter(v -> !v.omission())
                .map(RuleViolation::rule)
                .map(CounterOfferAgent::describe)
                .toList();
      }
      if (violated.isEmpty()) {
        return new Outcome(
            new Redline(
                redlineId,
                finding.id(),
                clause == null ? null : clause.id(),
                answer.path("action").asText("REPLACE"),
                clause == null ? null : clause.text(),
                SensitiveText.of(proposed),
                SensitiveText.of(answer.path("rationale").asText()),
                finding.citations(),
                checkable.stream().map(Rule::id).toList(),
                attempt,
                request.outputLanguage()),
            null,
            checkable.isEmpty());
      }
      problem =
          "The proposal breaks: " + String.join("; ", violated) + ". Fix it and keep the rest.";
    }
    return new Outcome(null, "rule-violation");
  }

  private static String describe(Rule rule) {
    return rule.id()
        + " ("
        + rule.fact()
        + " "
        + rule.comparator()
        + " "
        + rule.value()
        + " "
        + rule.unit()
        + ")";
  }

  private static Map<String, Object> variables(
      AnalysisRequest request,
      Finding finding,
      @Nullable Clause clause,
      List<Rule> activeRules,
      String problem) {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("side", request.side().name());
    variables.put("output_language", request.outputLanguage());
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("title", finding.title().reveal());
    summary.put("kind", finding.kind().name());
    summary.put("explanation", finding.explanation().reveal());
    List<Map<String, Object>> citations = new ArrayList<>();
    for (VerifiedCitation c : finding.citations()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("sectionKey", c.sectionKey());
      item.put("statuteText", c.quote());
      citations.add(item);
    }
    summary.put("verifiedCitations", citations);
    variables.put("finding", summary);
    List<Map<String, Object>> limits = new ArrayList<>();
    for (Rule rule : activeRules) {
      if (rule.clauseType().equals(finding.clauseType())) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ruleId", rule.id());
        item.put("fact", rule.fact().name());
        item.put("comparator", rule.comparator().name());
        item.put("value", rule.value());
        item.put("unit", rule.unit() == null ? null : rule.unit().name());
        limits.add(item);
      }
    }
    variables.put("limits", limits);
    variables.put("previous_attempt_problem", problem);
    Map<String, Object> clauseJson = new LinkedHashMap<>();
    if (clause != null) {
      clauseJson.put("id", clause.id());
      clauseJson.put("text", clause.text().reveal());
    } else {
      clauseJson.put("topic", finding.clauseType());
    }
    variables.put("clause", clauseJson);
    return variables;
  }
}
