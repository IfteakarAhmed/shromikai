package bd.shromikai.analysis;

import bd.shromikai.memo.MemoComposer;
import bd.shromikai.memo.MemoInput;
import bd.shromikai.memo.RedlineDiff;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.verify.SignedResultCodec;
import bd.shromikai.verify.VerifiedCitation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Turns a finished job into the {@code AnalysisResult} the owning subject receives (api.md §5.12,
 * FR-54, FR-55). This is the one place where placeholder names are restored: every text is
 * re-identified here, in the response, and nowhere else (architecture §10.4). The job keeps only
 * redacted values. The memo is composed from the re-identified texts and the whole result, minus
 * its {@code envelope}, is signed, so a client can send it back unchanged for rendering (ADR-0006).
 */
@Service
public class AnalysisResults {

  private final JobStore jobs;
  private final MemoComposer composer;
  private final SignedResultCodec codec;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final boolean recorded;
  private final AnalysisRunStore runs;
  private final bd.shromikai.trace.AnalysisTraceStore traces;

  public AnalysisResults(
      JobStore jobs,
      MemoComposer composer,
      SignedResultCodec codec,
      ObjectMapper mapper,
      Clock clock,
      Environment environment,
      AnalysisRunStore runs,
      bd.shromikai.trace.AnalysisTraceStore traces) {
    this.jobs = jobs;
    this.composer = composer;
    this.codec = codec;
    this.mapper = mapper;
    this.clock = clock;
    this.runs = runs;
    this.traces = traces;
    this.recorded =
        environment.acceptsProfiles(org.springframework.core.env.Profiles.of("fake-models"));
  }

  /**
   * The job of an analysis for its owner.
   *
   * @throws ProblemException {@code not-found} for another subject or an unknown ID, {@code
   *     analysis-expired} (410) for the owner after expiry or delete
   */
  public AnalysisJob jobFor(UUID id, AnalysisSubject caller) {
    AnalysisJob job = jobs.find(id).orElse(null);
    if (job == null) {
      throw new ProblemException(
          jobs.wasOwnedBy(id, caller) ? ProblemType.ANALYSIS_EXPIRED : ProblemType.NOT_FOUND);
    }
    AnalysisSubject owner = job.owner();
    if (owner == null || !owner.key().equals(caller.key())) {
      throw new ProblemException(ProblemType.NOT_FOUND);
    }
    return job;
  }

  /** Removes the job now (FR-54); the owner may delete again or after expiry, others get 404. */
  public void delete(UUID id, AnalysisSubject caller) {
    AnalysisJob job = jobs.find(id).orElse(null);
    if (job == null) {
      if (jobs.wasOwnedBy(id, caller)) {
        return;
      }
      throw new ProblemException(ProblemType.NOT_FOUND);
    }
    jobFor(id, caller);
    jobs.delete(id);
  }

  /**
   * The obligations checklist per party, re-identified, with its disclaimer (E-16, FR-53).
   *
   * @throws IllegalStateException when the analysis has not finished
   */
  public ObjectNode obligationsFor(UUID id, AnalysisSubject caller) {
    AnalysisJob job = jobFor(id, caller);
    AnalysisJob.Outcome outcome = job.outcome();
    AnalysisRequest request = job.request();
    if (job.status() != AnalysisJob.Status.ANALYSED || outcome == null || request == null) {
      throw new IllegalStateException("The analysis has not finished");
    }
    UnaryOperator<String> restore = text -> job.placeholders().restore(text);
    ObjectNode export = mapper.createObjectNode();
    export.put("analysisId", id.toString());
    ObjectNode parties = export.putObject("parties");
    ArrayNode all = obligations(outcome, restore);
    for (String party : List.of("EMPLOYER", "WORKER", "BOTH")) {
      ArrayNode list = parties.putArray(party);
      all.forEach(
          o -> {
            if (party.equals(o.path("party").asText())) {
              list.add(o);
            }
          });
    }
    export.put(
        "disclaimer",
        "bn".equals(request.outputLanguage())
            ? "এটা আইনি পরামর্শ নয়। এই তালিকা শুধু তথ্যের জন্য।"
            : "This is not legal advice. This checklist is for information only.");
    return export;
  }

  /**
   * The content-free trace of an analysis, kept 30 days (E-15, FR-48). Readable after the result
   * has expired; only the owner may read it (404 otherwise).
   */
  public ObjectNode traceFor(UUID id, AnalysisSubject caller) {
    AnalysisRunStore.RunInfo info =
        runs.info(id)
            .filter(r -> r.ownerKey().equals(caller.key()))
            .orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
    ObjectNode trace = mapper.createObjectNode();
    trace.put("analysisId", id.toString());
    trace.put("createdAt", info.createdAt().toString());
    ArrayNode events = trace.putArray("events");
    for (var e : traces.list(id)) {
      ObjectNode node = events.addObject();
      node.put("stage", e.stage());
      node.put("at", e.at().toString());
      node.put("durationMs", e.durationMs());
      node.set("promptId", mapper.valueToTree(e.promptId()));
      node.set("promptVersion", mapper.valueToTree(e.promptVersion()));
      node.set("modelId", mapper.valueToTree(e.modelId()));
      node.set("tokensIn", mapper.valueToTree(e.tokensIn()));
      node.set("tokensOut", mapper.valueToTree(e.tokensOut()));
      node.set("cacheReadTokens", mapper.valueToTree(e.detail().get("cacheReadTokens")));
      node.set("cacheWriteTokens", mapper.valueToTree(e.detail().get("cacheWriteTokens")));
      node.set("costUsdMicros", mapper.valueToTree(e.costUsdMicros()));
      node.put("outcome", e.outcome());
      node.set("detail", mapper.valueToTree(e.detail()));
    }
    ObjectNode totals = trace.putObject("totals");
    totals.put("tokensIn", info.tokensIn());
    totals.put("tokensOut", info.tokensOut());
    totals.put("cacheReadTokens", info.cacheReadTokens());
    totals.put("cacheWriteTokens", info.cacheWriteTokens());
    totals.put("costUsdMicros", info.costUsdMicros());
    return trace;
  }

  /** The signed, re-identified result of a finished analysis for its owner. */
  public ObjectNode resultFor(UUID id, AnalysisSubject caller) {
    AnalysisJob job = jobFor(id, caller);
    AnalysisJob.Outcome outcome = job.outcome();
    AnalysisRequest request = job.request();
    Instant completedAt = job.completedAt();
    if (job.status() != AnalysisJob.Status.ANALYSED
        || outcome == null
        || request == null
        || completedAt == null) {
      throw new IllegalStateException("The analysis has not finished");
    }
    UnaryOperator<String> restore = text -> job.placeholders().restore(text);
    ObjectNode result = mapper.createObjectNode();
    result.put("analysisId", job.id().toString());
    result.put("status", "SUCCEEDED");
    result.put("createdAt", job.createdAt().toString());
    result.put("completedAt", completedAt.toString());
    result.put("expiresAt", completedAt.plus(JobStore.TTL).toString());
    result.put("side", request.side().name());
    result.put("outputLanguage", request.outputLanguage());
    if (request.profile().isEmpty()) {
      result.putNull("profile");
    } else {
      result.set("profile", mapper.valueToTree(request.profile()));
    }
    ObjectNode input = result.putObject("input");
    input.put("kind", job.inputKind());
    input.put("pageCount", job.pageCount());
    input.put("detectedLanguage", job.language() == null ? "bn" : job.language());
    input.putNull("ocrMeanConfidence");
    input.put(
        "legacyFontConverted",
        job.clauses().stream().anyMatch(c -> c.flags().contains(ClauseFlag.LEGACY_FONT_CONVERTED)));
    input.put("sample", false);
    result.put("documentType", "EMPLOYMENT_CONTRACT");
    result.set("clauses", clauses(job, restore));
    result.set("findings", findings(outcome, restore));
    result.set("redlines", redlines(outcome, restore, request.outputLanguage()));
    result.set("obligations", obligations(outcome, restore));
    MemoNarrator.Narrative narrative = outcome.narrative();
    ObjectNode summary = summary(outcome);
    result.set("summary", summary);
    result.putNull("reviewTicket");
    ArrayNode warnings = result.putArray("warnings");
    outcome
        .warnings()
        .forEach(w -> warnings.addObject().put("code", w.code()).put("message", w.message()));
    ObjectNode provenance = result.putObject("provenance");
    provenance.put("pipelineVersion", AnalysisPipeline.PIPELINE_VERSION);
    provenance.put("corpusRelease", outcome.corpusRelease());
    provenance.set("promptVersions", mapper.valueToTree(outcome.promptVersions()));
    provenance.set("models", mapper.valueToTree(outcome.models()));
    provenance.put("verifierIndependence", outcome.verifierIndependence());
    provenance.put("recorded", recorded);
    result.put("costUsd", outcome.costUsdMicros() / 1_000_000.0);

    MemoInput memoInput =
        new MemoInput(
            UUID.randomUUID(),
            job.id(),
            request.outputLanguage(),
            MemoNarrator.readingLevel(request),
            completedAt,
            restore.apply(narrative.summary().reveal()),
            narrative.nextSteps().stream().map(s -> restore.apply(s.reveal())).toList(),
            memoFindings(outcome, restore),
            outcome.redlines().stream().map(Redline::id).toList(),
            outcome.obligations().stream().map(ObligationItem::id).toList(),
            new MemoInput.Verification(
                summary.path("verifiedCitations").asInt(),
                summary.path("uncertainFindings").asInt(),
                summary.path("policyFindings").asInt(),
                outcome.verifierIndependence()),
            new MemoInput.Footer(
                outcome.corpusRelease(), AnalysisPipeline.PIPELINE_VERSION, codec.keyId()));
    ObjectNode memo = composer.compose(memoInput);
    result.set("memo", memo);
    result.put("disclaimer", memo.path("disclaimer").asText());

    SignedResultCodec.Envelope envelope = codec.sign(result);
    ObjectNode envelopeNode = result.putObject("envelope");
    envelopeNode.put("keyId", envelope.keyId());
    envelopeNode.put("algorithm", envelope.algorithm());
    envelopeNode.put("expiresAt", envelope.expiresAt().toString());
    envelopeNode.put("signature", envelope.signature());
    return result;
  }

  private ArrayNode clauses(AnalysisJob job, UnaryOperator<String> restore) {
    ArrayNode out = mapper.createArrayNode();
    int ordinal = 1;
    for (Clause c : job.clauses()) {
      ObjectNode node = out.addObject();
      node.put("id", c.id());
      node.put("ordinal", ordinal++);
      if (c.label() == null) {
        node.putNull("label");
      } else {
        node.put("label", c.label());
      }
      node.put("type", String.valueOf(c.type() == null ? ClauseType.OTHER : c.type()));
      node.put("typeConfidence", c.confidence());
      node.putArray("secondaryTypes");
      node.putNull("heading");
      node.put("text", restore.apply(c.text().reveal()));
      node.set("paragraphIds", mapper.valueToTree(c.paragraphIds()));
      node.putArray("pages");
      node.put("language", job.language() == null ? "bn" : job.language());
      node.putArray("facts");
      ArrayNode flags = node.putArray("flags");
      c.flags().stream().map(Enum::name).sorted().forEach(flags::add);
      node.put("segmentation", "RULE_BASED");
    }
    return out;
  }

  private ArrayNode findings(AnalysisJob.Outcome outcome, UnaryOperator<String> restore) {
    ArrayNode out = mapper.createArrayNode();
    for (Finding f : outcome.findings()) {
      ObjectNode node = out.addObject();
      node.put("id", f.id());
      node.put("title", restore.apply(f.title().reveal()));
      node.put("kind", f.kind().name());
      node.put("severity", f.severity().name());
      node.put("nonObvious", f.nonObvious());
      node.put("source", f.source().name());
      node.put("clauseType", f.clauseType());
      node.set("clauseIds", mapper.valueToTree(f.clauseIds()));
      node.put("explanation", restore.apply(f.explanation().reveal()));
      node.put("whyItMatters", restore.apply(f.whyItMatters().reveal()));
      node.putNull("sideNote");
      node.put("verificationStatus", f.verification().name());
      node.set("citations", mapper.valueToTree(f.citations()));
      node.putArray("iloReferences");
      if (f.ruleId() == null) {
        node.putNull("ruleId");
      } else {
        node.put("ruleId", f.ruleId());
      }
      node.putNull("playbookRuleId");
      if (f.redlineId() == null) {
        node.putNull("redlineId");
      } else {
        node.put("redlineId", f.redlineId());
      }
      if (f.reviewItemId() == null) {
        node.putNull("reviewItemId");
      } else {
        node.put("reviewItemId", f.reviewItemId().toString());
      }
      node.put("confidence", f.confidence());
    }
    return out;
  }

  private ArrayNode redlines(
      AnalysisJob.Outcome outcome, UnaryOperator<String> restore, String language) {
    ArrayNode out = mapper.createArrayNode();
    for (Redline r : outcome.redlines()) {
      String original = r.originalText() == null ? null : restore.apply(r.originalText().reveal());
      String proposed = restore.apply(r.proposedText().reveal());
      ObjectNode node = out.addObject();
      node.put("id", r.id());
      node.set("findingIds", mapper.valueToTree(List.of(r.findingId())));
      if (r.clauseId() == null) {
        node.putNull("clauseId");
      } else {
        node.put("clauseId", r.clauseId());
      }
      node.put("action", r.action());
      if (original == null) {
        node.putNull("originalText");
      } else {
        node.put("originalText", original);
      }
      node.put("proposedText", proposed);
      ArrayNode diff = node.putArray("diff");
      for (RedlineDiff.Segment segment :
          RedlineDiff.words(original == null ? "" : original, proposed)) {
        diff.addObject().put("op", segment.op().name()).put("text", segment.text());
      }
      node.put("rationale", restore.apply(r.rationale().reveal()));
      node.set("citations", mapper.valueToTree(r.citations()));
      ObjectNode check = node.putObject("ruleCheck");
      check.put("passed", true);
      check.set("ruleIds", mapper.valueToTree(r.ruleIds()));
      check.put("attempts", r.attempts());
      node.put("language", language);
    }
    return out;
  }

  private ArrayNode obligations(AnalysisJob.Outcome outcome, UnaryOperator<String> restore) {
    ArrayNode out = mapper.createArrayNode();
    for (ObligationItem o : outcome.obligations()) {
      ObjectNode node = out.addObject();
      node.put("id", o.id());
      node.put("party", o.party());
      node.put("obligation", restore.apply(o.obligation().reveal()));
      if (o.trigger() == null) {
        node.putNull("trigger");
      } else {
        node.put("trigger", restore.apply(o.trigger().reveal()));
      }
      if (o.deadline() == null) {
        node.putNull("deadline");
      } else {
        node.putObject("deadline")
            .put("value", o.deadline().value())
            .put("unit", o.deadline().unit());
      }
      node.put("source", o.source());
      node.put("status", o.status());
      node.set("clauseIds", mapper.valueToTree(o.clauseIds()));
      node.set("citations", mapper.valueToTree(o.citations()));
    }
    return out;
  }

  private ObjectNode summary(AnalysisJob.Outcome outcome) {
    Map<SeverityRank, Integer> bySeverity = new EnumMap<>(SeverityRank.class);
    for (SeverityRank s : SeverityRank.values()) {
      bySeverity.put(s, 0);
    }
    int nonObvious = 0;
    int verified = 0;
    int uncertain = 0;
    int policy = 0;
    int legal = 0;
    int citations = 0;
    SeverityRank overall = null;
    for (Finding f : outcome.findings()) {
      SeverityRank s = SeverityRank.valueOf(f.severity().name());
      bySeverity.merge(s, 1, Integer::sum);
      if (overall == null || s.ordinal() < overall.ordinal()) {
        overall = s;
      }
      nonObvious += f.nonObvious() ? 1 : 0;
      verified += f.verification() == FindingVerification.VERIFIED ? 1 : 0;
      uncertain += f.verification() == FindingVerification.UNCERTAIN ? 1 : 0;
      policy += f.verification() == FindingVerification.POLICY_ONLY ? 1 : 0;
      legal += f.verification() == FindingVerification.POLICY_ONLY ? 0 : 1;
      citations += f.citations().size();
    }
    ObjectNode node = mapper.createObjectNode();
    if (overall == null) {
      node.putNull("overallSeverity");
    } else {
      node.put("overallSeverity", overall.name());
    }
    ObjectNode counts = node.putObject("findingsBySeverity");
    for (SeverityRank s : SeverityRank.values()) {
      counts.put(s.name(), bySeverity.get(s));
    }
    node.put("nonObviousFindings", nonObvious);
    node.put("verifiedFindings", verified);
    node.put("uncertainFindings", uncertain);
    node.put("policyFindings", policy);
    node.put("verifiedCitations", citations);
    boolean few = legal < 3;
    if (few) {
      node.put("coverageNote", "Fewer than three risks were found; none were invented.");
    } else {
      node.putNull("coverageNote");
    }
    return node;
  }

  private List<MemoInput.FindingView> memoFindings(
      AnalysisJob.Outcome outcome, UnaryOperator<String> restore) {
    List<MemoInput.FindingView> out = new ArrayList<>();
    for (Finding f : outcome.findings()) {
      boolean verified = f.verification() == FindingVerification.VERIFIED;
      out.add(
          new MemoInput.FindingView(
              f.id(),
              restore.apply(f.title().reveal()),
              f.severity().name(),
              f.source().name(),
              f.verification().name(),
              restore.apply(f.explanation().reveal()),
              restore.apply(f.whyItMatters().reveal()),
              verified
                  ? f.citations().stream().map(VerifiedCitation::citationId).toList()
                  : List.of(),
              f.redlineId()));
    }
    return out;
  }

  /** Severities from most to least serious, for the summary counts. */
  private enum SeverityRank {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
  }
}
