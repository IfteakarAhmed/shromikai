package bd.shromikai.eval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The content-free results of a run ({@code results.jsonl}, evaluation.md §5): case IDs, finding
 * IDs, clause labels, section keys, statuses, reason codes, scores, timings and costs. Never
 * contract text, never a quote or claim (R-02).
 */
public final class EvalResults {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .findAndRegisterModules()
          .setSerializationInclusion(JsonInclude.Include.ALWAYS)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public static final String FILE = "results.jsonl";

  private EvalResults() {}

  /** A citation shown to the user (deduplicated by citation ID in M-01). */
  public record ShownCitation(String citationId, String sectionKey, String status) {}

  /** A claimed citation and how the verifier ended it; origin LLM, RULE or BASELINE. */
  public record ClaimRecord(
      String findingId,
      String sectionKey,
      int attempt,
      String status,
      List<String> reasonCodes,
      String origin) {}

  /** A predicted finding with printed clause labels and its verified section keys. */
  public record PredictedFinding(
      String id,
      List<String> clauseLabels,
      String clauseType,
      String kind,
      String severity,
      String verification,
      double confidence,
      List<String> verifiedSectionKeys,
      String source) {}

  /** A clause as segmented and classified (M-23). */
  public record ClauseRecord(String label, String type) {}

  /** One contract, one side, one system. */
  public record AnalysisCase(
      String contractId,
      String system,
      String side,
      @Nullable String language,
      String outcome,
      @Nullable String failureCode,
      boolean recorded,
      @Nullable String piiMode,
      List<PredictedFinding> findings,
      List<ShownCitation> shown,
      List<ClaimRecord> claims,
      List<ClauseRecord> clauses,
      long latencyMs,
      Map<String, Long> stageMs,
      long costMicros,
      long liveCostMicros,
      int liveCalls,
      int replayedCalls,
      int cloudRequests,
      int canaryHits,
      Map<String, String> models,
      Map<String, Integer> promptVersions,
      List<String> warnings) {

    public static Builder builder(String contractId, String system, String side) {
      return new Builder(contractId, system, side);
    }
  }

  /** Builder, so tests and the harness set only what they know. */
  public static final class Builder {
    private final String contractId;
    private final String system;
    private final String side;
    private @Nullable String language;
    private String outcome = "ANALYSED";
    private @Nullable String failureCode;
    private boolean recorded;
    private @Nullable String piiMode;
    private List<PredictedFinding> findings = List.of();
    private List<ShownCitation> shown = List.of();
    private List<ClaimRecord> claims = List.of();
    private List<ClauseRecord> clauses = List.of();
    private long latencyMs;
    private Map<String, Long> stageMs = Map.of();
    private long costMicros;
    private long liveCostMicros;
    private int liveCalls;
    private int replayedCalls;
    private int cloudRequests;
    private int canaryHits;
    private Map<String, String> models = Map.of();
    private Map<String, Integer> promptVersions = Map.of();
    private List<String> warnings = List.of();

    Builder(String contractId, String system, String side) {
      this.contractId = contractId;
      this.system = system;
      this.side = side;
    }

    public Builder language(@Nullable String v) {
      language = v;
      return this;
    }

    public Builder outcome(String v) {
      outcome = v;
      return this;
    }

    public Builder failureCode(@Nullable String v) {
      failureCode = v;
      return this;
    }

    public Builder recorded(boolean v) {
      recorded = v;
      return this;
    }

    public Builder piiMode(@Nullable String v) {
      piiMode = v;
      return this;
    }

    public Builder findings(List<PredictedFinding> v) {
      findings = List.copyOf(v);
      return this;
    }

    public Builder shown(List<ShownCitation> v) {
      shown = List.copyOf(v);
      return this;
    }

    public Builder claims(List<ClaimRecord> v) {
      claims = List.copyOf(v);
      return this;
    }

    public Builder clauses(List<ClauseRecord> v) {
      clauses = List.copyOf(v);
      return this;
    }

    public Builder latencyMs(long v) {
      latencyMs = v;
      return this;
    }

    public Builder stageMs(Map<String, Long> v) {
      stageMs = Map.copyOf(v);
      return this;
    }

    public Builder costMicros(long v) {
      costMicros = v;
      return this;
    }

    public Builder liveCostMicros(long v) {
      liveCostMicros = v;
      return this;
    }

    public Builder liveCalls(int v) {
      liveCalls = v;
      return this;
    }

    public Builder replayedCalls(int v) {
      replayedCalls = v;
      return this;
    }

    public Builder cloudRequests(int v) {
      cloudRequests = v;
      return this;
    }

    public Builder canaryHits(int v) {
      canaryHits = v;
      return this;
    }

    public Builder models(Map<String, String> v) {
      models = Map.copyOf(v);
      return this;
    }

    public Builder promptVersions(Map<String, Integer> v) {
      promptVersions = Map.copyOf(v);
      return this;
    }

    public Builder warnings(List<String> v) {
      warnings = List.copyOf(v);
      return this;
    }

    public AnalysisCase build() {
      return new AnalysisCase(
          contractId,
          system,
          side,
          language,
          outcome,
          failureCode,
          recorded,
          piiMode,
          findings,
          shown,
          claims,
          clauses,
          latencyMs,
          stageMs,
          costMicros,
          liveCostMicros,
          liveCalls,
          replayedCalls,
          cloudRequests,
          canaryHits,
          models,
          promptVersions,
          warnings);
    }
  }

  /** One adversarial citation and the verifier's answer (M-04, M-05). */
  public record CitationCase(
      String id,
      String cls,
      String actualStatus,
      List<String> reasonCodes,
      @Nullable String supportLabel,
      boolean recorded) {

    public boolean fabricated() {
      return !"VALID_CONTROL".equals(cls);
    }
  }

  /** Local redaction of one contract (M-25) and the content-at-rest scan (M-17). */
  public record PiiCase(
      String contractId,
      String mode,
      long redactMs,
      Map<String, List<Long>> replacedAndTotalByType,
      int atRestHits,
      List<String> atRestPlaces) {}

  /** Everything in one results file. */
  public record Bundle(
      List<AnalysisCase> analyses, List<CitationCase> citations, List<PiiCase> pii) {}

  public static void write(Path dir, List<AnalysisCase> analyses, List<CitationCase> citations) {
    try {
      Files.createDirectories(dir);
      Files.deleteIfExists(dir.resolve(FILE));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    append(dir, analyses, citations, List.of());
  }

  /** Adds results to the run's file (a run may be built over several invocations). */
  public static synchronized void append(
      Path dir, List<AnalysisCase> analyses, List<CitationCase> citations, List<PiiCase> pii) {
    List<String> lines = new ArrayList<>();
    for (AnalysisCase c : analyses) {
      lines.add(line("analysis", c));
    }
    for (CitationCase c : citations) {
      lines.add(line("citation", c));
    }
    for (PiiCase c : pii) {
      lines.add(line("pii", c));
    }
    try {
      Files.createDirectories(dir);
      Files.write(
          dir.resolve(FILE),
          lines,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String line(String type, Object value) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("type", type);
    node.setAll((ObjectNode) MAPPER.valueToTree(value));
    try {
      return MAPPER.writeValueAsString(node);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Bundle read(Path dir) {
    List<AnalysisCase> analyses = new ArrayList<>();
    List<CitationCase> citations = new ArrayList<>();
    List<PiiCase> pii = new ArrayList<>();
    Path file = dir.resolve(FILE);
    if (!Files.isRegularFile(file)) {
      return new Bundle(analyses, citations, pii);
    }
    try {
      for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (line.isBlank()) {
          continue;
        }
        JsonNode node = MAPPER.readTree(line);
        switch (node.path("type").asText()) {
          case "analysis" -> analyses.add(MAPPER.treeToValue(node, AnalysisCase.class));
          case "citation" -> citations.add(MAPPER.treeToValue(node, CitationCase.class));
          case "pii" -> pii.add(MAPPER.treeToValue(node, PiiCase.class));
          default -> throw new IllegalStateException("Unknown result type in " + file);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new Bundle(analyses, citations, pii);
  }

  /** For reports: a map in insertion order. */
  static Map<String, Object> map(Object... keyValues) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      out.put((String) keyValues[i], keyValues[i + 1]);
    }
    return out;
  }
}
