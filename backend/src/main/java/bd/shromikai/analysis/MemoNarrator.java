package bd.shromikai.analysis;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.ModelException;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The model part of the memo (FR-51): the summary and the next steps, from {@code memo-bn} or
 * {@code memo-en}. The prompt is told the reading level of the profile (FR-56); it changes wording
 * only, the findings it is given are the same at every level. When the model cannot answer, the
 * narrative is a fixed text built without a model (ADR-0020); an egress block or a spent budget
 * still ends the analysis (R-03).
 */
@Component
public final class MemoNarrator {

  /** The summary and steps (redacted, placeholders not yet restored) and where they came from. */
  public record Narrative(SensitiveText summary, List<SensitiveText> nextSteps, boolean fromModel) {

    public Narrative {
      nextSteps = List.copyOf(nextSteps);
    }
  }

  static String promptFor(String language) {
    return "memo-" + language;
  }

  public Narrative narrate(ModelSession session, AnalysisRequest request, List<Finding> findings) {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("side", request.side().name());
    variables.put("reading_level", readingLevel(request));
    variables.put("profile", request.profile());
    variables.put("counts", counts(findings));
    variables.put("findings", findingJson(findings));
    try {
      ModelGateway.Result result =
          session.call(Stage.COMPOSE, promptFor(request.outputLanguage()), variables);
      JsonNode out = result.output();
      if ("OK".equals(out.path("status").asText()) && !out.path("summary").asText().isBlank()) {
        List<SensitiveText> steps = new ArrayList<>();
        out.path("next_steps").forEach(s -> steps.add(SensitiveText.of(s.asText())));
        if (!citesStatute(out.path("summary").asText(), steps)) {
          return new Narrative(SensitiveText.of(out.path("summary").asText()), steps, true);
        }
      }
    } catch (EgressBlockedException blocked) {
      throw blocked;
    } catch (ProblemException problem) {
      if (problem.type() == ProblemType.BUDGET_EXCEEDED || !(problem instanceof ModelException)) {
        throw problem;
      }
    }
    return fallback(request.outputLanguage(), findings);
  }

  /** A section sign, "section N" or "ধারা N" with Latin or Bangla digits (ধারা is "section"). */
  private static final java.util.regex.Pattern STATUTE_REFERENCE =
      java.util.regex.Pattern.compile(
          "§|\\bsections?\\b\\s*[\\d০-৯]|ধারা\\s*[\\d০-৯]",
          java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

  /**
   * The narrative is the one memo text that no citation verifier sees (R-01), so a statute
   * reference in it is refused and the fixed narrative is used instead.
   */
  static boolean citesStatute(String summary, List<SensitiveText> steps) {
    if (STATUTE_REFERENCE.matcher(summary).find()) {
      return true;
    }
    return steps.stream().anyMatch(s -> STATUTE_REFERENCE.matcher(s.reveal()).find());
  }

  static String readingLevel(AnalysisRequest request) {
    return "BASIC".equalsIgnoreCase(request.profile().get("reading_level")) ? "BASIC" : "STANDARD";
  }

  static Map<String, Object> counts(List<Finding> findings) {
    Map<String, Object> counts = new LinkedHashMap<>();
    for (String severity : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
      counts.put(
          severity, findings.stream().filter(f -> f.severity().name().equals(severity)).count());
    }
    counts.put(
        "verified",
        findings.stream().filter(f -> f.verification() == FindingVerification.VERIFIED).count());
    counts.put(
        "uncertain",
        findings.stream().filter(f -> f.verification() == FindingVerification.UNCERTAIN).count());
    return counts;
  }

  private static List<Map<String, Object>> findingJson(List<Finding> findings) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Finding f : findings) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", f.id());
      item.put("title", f.title().reveal());
      item.put("severity", f.severity().name());
      item.put("status", f.verification().name());
      item.put("explanation", f.explanation().reveal());
      out.add(item);
    }
    return out;
  }

  /** A fixed narrative with counts only; no model, no contract text. */
  static Narrative fallback(String language, List<Finding> findings) {
    long count = findings.size();
    long uncertain =
        findings.stream().filter(f -> f.verification() == FindingVerification.UNCERTAIN).count();
    if ("bn".equals(language)) {
      return new Narrative(
          SensitiveText.of(
              "এই চুক্তিতে "
                  + count
                  + "টি ঝুঁকি পাওয়া গেছে। তার মধ্যে "
                  + uncertain
                  + "টি অনিশ্চিত, মানুষ সেগুলো পর্যালোচনা করবেন। নিচের তালিকায় প্রতিটি ঝুঁকি দেখুন।"),
          List.of(
              SensitiveText.of("প্রতিটি ঝুঁকির ব্যাখ্যা পড়ুন।"),
              SensitiveText.of("প্রস্তাবিত সংশোধিত ধারা নিয়োগকর্তার সঙ্গে আলোচনা করুন।"),
              SensitiveText.of("সিদ্ধান্তের আগে একজন আইনজীবী বা শ্রম কার্যালয়ের সঙ্গে কথা বলুন।")),
          false);
    }
    return new Narrative(
        SensitiveText.of(
            count
                + " risks were found in this contract. "
                + uncertain
                + " of them are uncertain and a person will review them. See each risk below."),
        List.of(
            SensitiveText.of("Read the explanation of each risk."),
            SensitiveText.of("Discuss the suggested revised clauses with the employer."),
            SensitiveText.of("Speak to a lawyer or a labour office before you decide.")),
        false);
  }
}
