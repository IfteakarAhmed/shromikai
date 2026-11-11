package bd.shromikai.testsupport;

import bd.shromikai.privacy.LocalEntities;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.text.BanglaNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scripted stand-ins for every prompt of the pipeline, installed on the {@link FakeChatPort}. They
 * are <strong>not recorded model output</strong>: they read the request and answer by simple
 * keyword rules over the fictional corpus, so integration tests can run the real pipeline without a
 * cloud call. What they prove is the pipeline's behaviour (verification, correction, review items,
 * redlines), never the quality of a model.
 */
public final class P6Stubs {

  private static final Pattern CLAUSES =
      Pattern.compile("<contract_clause>\\s*(.*?)\\s*</contract_clause>", Pattern.DOTALL);
  private static final Pattern SECTIONS =
      Pattern.compile("<statute_sections>\\s*(.*?)\\s*</statute_sections>", Pattern.DOTALL);
  private static final Pattern SIDE = Pattern.compile("side: (WORKER|EMPLOYER)");
  private static final Pattern NUMBER_BEFORE =
      Pattern.compile("(\\d+)\\s*(hours|ঘণ্টা|months?|মাস|days?|দিনের|years?|বছর)");

  private P6Stubs() {}

  /** Installs answers for every prompt; entities are those of all synthetic contracts. */
  public static void install(FakeChatPort fake) {
    LocalEntities all = LocalEntities.NONE;
    for (String id : SyntheticContracts.IDS) {
      all = all.merge(AnalysisHarness.entitiesOf(id));
    }
    LocalEntities union = all;
    fake.respondWith("pii-extract", request -> AnalysisHarness.piiAnswer(union));
    fake.respondWith(
        "clause-classify",
        request -> AnthropicStub.answer(request.system() + "\n" + request.user().reveal()));
    fake.respondWith(
        "clause-segment",
        request -> AnthropicStub.answer(request.system() + "\n" + request.user().reveal()));
    fake.respondWith("query-rewrite", P6Stubs::queryRewrite);
    fake.respondWith("clause-facts", P6Stubs::facts);
    fake.respondWith("risk-analysis", P6Stubs::risk);
    fake.respondWith("risk-citation-repair", P6Stubs::repair);
    fake.respondWith("counter-offer", P6Stubs::counterOffer);
    fake.respondWith(
        "verify-support",
        request ->
            TestGateways.json(
                "{\"label\":\"SUPPORTS\",\"confidence\":0.9,\"reason\":\"stand-in\"}"));
    fake.respondWith(
        "verify-support-claude",
        request ->
            TestGateways.json(
                "{\"label\":\"SUPPORTS\",\"confidence\":0.9,\"reason\":\"stand-in\"}"));
  }

  // ------------------------------------------------------------------ helpers

  public static JsonNode clausesOf(ChatRequest request) {
    return clauses(request);
  }

  public static JsonNode candidatesOf(ChatRequest request) {
    return candidates(request);
  }

  static JsonNode clauses(ChatRequest request) {
    Matcher m = CLAUSES.matcher(request.user().reveal());
    if (!m.find()) {
      return TestGateways.json("[]");
    }
    return TestGateways.json(unescape(m.group(1)));
  }

  static JsonNode candidates(ChatRequest request) {
    Matcher m = SECTIONS.matcher(request.user().reveal());
    return m.find() ? TestGateways.json(m.group(1)) : TestGateways.json("[]");
  }

  static String side(ChatRequest request) {
    Matcher m = SIDE.matcher(request.user().reveal());
    return m.find() ? m.group(1) : "WORKER";
  }

  private static String unescape(String s) {
    return s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
  }

  /** The text of a clause in matching form with Latin digits, lower case. */
  private static String plain(String text) {
    return BanglaNormalizer.toLatinDigits(text).toLowerCase(Locale.ROOT);
  }

  // ------------------------------------------------------------------ prompts

  private static JsonNode queryRewrite(ChatRequest request) {
    String text = plain(request.user().reveal());
    String query =
        text.contains("probation") || text.contains("শিক্ষানবিশ")
            ? "probation period dismissed"
            : text.contains("notice") || text.contains("নোটিশ")
                ? "written notice of dismissal"
                : text.contains("overtime") || text.contains("অতিরিক্ত")
                    ? "overtime paid ordinary rate"
                    : text.contains("hours") || text.contains("ঘণ্টা")
                        ? "ordinary work hours a day"
                        : text.contains("deduct") || text.contains("কর্তন")
                            ? "wages deduct paid within"
                            : text.contains("compet") || text.contains("প্রতিযোগিতা")
                                ? "agreement restrains worker employment"
                                : text.contains("maternity")
                                    ? "maternity leave woman worker"
                                    : text.contains("dispute") || text.contains("বিরোধ")
                                        ? "dispute labour court"
                                        : "worker employer";
    ObjectNode out = TestGateways.MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE").putArray("queries").add(query);
    return out;
  }

  private static JsonNode facts(ChatRequest request) {
    ObjectNode out = TestGateways.MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode clauses = out.putArray("clauses");
    for (JsonNode clause : clauses(request)) {
      ObjectNode item = clauses.addObject().put("id", clause.path("id").asText());
      ArrayNode facts = item.putArray("facts");
      String text = plain(clause.path("text").asText());
      Matcher m = NUMBER_BEFORE.matcher(text);
      while (m.find()) {
        double value = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        String parameter = null;
        String unitName = null;
        if ((unit.equals("hours") || unit.equals("ঘণ্টা"))
            && (text.contains("a day") || text.contains("দৈনিক"))) {
          parameter = "DAILY_WORKING_HOURS";
          unitName = "HOURS_PER_DAY";
        } else if ((unit.startsWith("month") || unit.equals("মাস"))
            && (text.contains("probation") || text.contains("শিক্ষানবিশ"))) {
          parameter = "PROBATION_LENGTH";
          unitName = "MONTHS";
        } else if ((unit.startsWith("day") || unit.equals("দিনের"))
            && (text.contains("notice") || text.contains("নোটিশ"))
            && !text.contains("without notice")) {
          parameter = "NOTICE_BY_EMPLOYER";
          unitName = "DAYS";
        }
        if (parameter != null) {
          facts
              .addObject()
              .put("parameter", parameter)
              .put("value", value)
              .put("unit", unitName)
              .put("confidence", 0.9);
        }
      }
    }
    return out;
  }

  /** The section a clause of this type is about, or null. */
  private static String keywordOf(String clauseType) {
    return switch (clauseType) {
      case "PROBATION" -> "probation";
      case "WAGE_PAYMENT_AND_DEDUCTIONS", "WAGES" -> "Wages shall be paid";
      case "OVERTIME" -> "Overtime";
      case "NOTICE_AND_RESIGNATION", "TERMINATION" -> "written notice of dismissal";
      case "NON_COMPETE" -> "restrains a worker";
      case "WORKING_HOURS" -> "Ordinary work";
      case "DISPUTE_RESOLUTION" -> "Labour Court";
      default -> null;
    };
  }

  private static JsonNode risk(ChatRequest request) {
    String side = side(request);
    JsonNode candidates = candidates(request);
    ObjectNode out = TestGateways.MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode findings = out.putArray("findings");
    for (JsonNode clause : clauses(request)) {
      String type = clause.path("type").asText();
      String keyword = keywordOf(type);
      if (keyword == null) {
        continue;
      }
      JsonNode section = null;
      for (JsonNode c : candidates) {
        if (c.path("authoritativeText").asText().contains(keyword)) {
          section = c;
        }
      }
      ObjectNode finding = findings.addObject();
      finding.put("title", "Check clause " + clause.path("id").asText() + " (" + type + ")");
      finding.put("kind", "CONFLICT");
      finding.put("severity", "HIGH");
      finding.put("non_obvious", false);
      finding.put("clause_type", type);
      finding.putArray("clause_ids").add(clause.path("id").asText());
      finding.put(
          "explanation", "For the " + side + ": the clause may conflict with the cited section.");
      finding.put("why_it_matters", "A statutory right may not be honoured.");
      ArrayNode citations = finding.putArray("citations");
      if (section != null) {
        String text = section.path("authoritativeText").asText();
        boolean invent = type.equals("DISPUTE_RESOLUTION");
        citations
            .addObject()
            .put("section_key", section.path("sectionKey").asText())
            .put(
                "quote",
                invent
                    ? "every worker may keep any machine they operate after working hours"
                    : text.substring(
                        text.indexOf('.') + 2,
                        Math.min(text.length(), text.indexOf('.') + 2 + 110)))
            .put(
                "claim",
                "The cited section bears on the clause "
                    + clause.path("id").asText()
                    + " of this contract.");
      }
      finding.put("confidence", 0.8);
    }
    ObjectNode omission = findings.addObject();
    omission.put("title", "Missing maternity leave clause");
    omission.put("kind", "OMISSION");
    omission.put("severity", "MEDIUM");
    omission.put("non_obvious", true);
    omission.put("clause_type", "MATERNITY_BENEFIT");
    omission.putArray("clause_ids");
    omission.put(
        "explanation", "For the " + side + ": the contract says nothing about maternity leave.");
    omission.put("why_it_matters", "A woman worker may not get the leave the law gives.");
    omission.putArray("citations");
    omission.put("confidence", 0.7);
    return out;
  }

  /** The finding the repair or counter-offer request is about (its title is enough here). */
  private static String findingTitle(ChatRequest request) {
    Matcher m = Pattern.compile("finding[^:\\n]*: (\\{.*})").matcher(request.user().reveal());
    if (!m.find()) {
      return "";
    }
    try {
      return TestGateways.json(m.group(1)).path("title").asText("").toLowerCase(Locale.ROOT);
    } catch (RuntimeException e) {
      return "";
    }
  }

  /** SUPPORTS when a significant word of the claim is in the section text, else UNRELATED. */
  private static JsonNode verifySupport(ChatRequest request) {
    String user = request.user().reveal();
    Matcher claimMatch = Pattern.compile("Claim: (.*)").matcher(user);
    String claim = claimMatch.find() ? claimMatch.group(1).toLowerCase(Locale.ROOT) : "";
    String section =
        user.substring(0, Math.max(0, user.indexOf("<contract_clause>"))).toLowerCase(Locale.ROOT);
    boolean supports = false;
    for (String word : claim.split("[^a-z]+")) {
      if (word.length() > 5
          && !java.util.List.of("section", "cited", "clause", "contract", "worker", "under")
              .contains(word)
          && section.contains(word)) {
        supports = true;
      }
    }
    return TestGateways.json(
        "{\"label\":\""
            + (supports ? "SUPPORTS" : "UNRELATED")
            + "\",\"confidence\":0.9,\"reason\":\"stand-in\"}");
  }

  private static JsonNode repair(ChatRequest request) {
    ObjectNode out = TestGateways.MAPPER.createObjectNode();
    JsonNode candidates = candidates(request);
    String all = findingTitle(request);
    if (all.contains("maternity")) {
      for (JsonNode c : candidates) {
        String text = c.path("authoritativeText").asText();
        if (text.contains("maternity")) {
          out.put("status", "OK").put("reason", "NONE");
          out.putArray("citations")
              .addObject()
              .put("section_key", c.path("sectionKey").asText())
              .put("quote", "entitled to sixteen weeks of paid maternity leave")
              .put(
                  "claim",
                  "A woman worker is entitled to paid maternity leave under the cited section.");
          return out;
        }
      }
    }
    out.put("status", "ABSTAIN").put("reason", "NO_SUPPORTING_SECTION").putArray("citations");
    return out;
  }

  private static JsonNode counterOffer(ChatRequest request) {
    String user = request.user().reveal().toLowerCase(Locale.ROOT);
    String text =
        user.contains("notice")
            ? "The employer may end the contract on 14 days written notice."
            : user.contains("probation")
                ? "The probation period is 3 months."
                : user.contains("ordinary work") || user.contains("hours")
                    ? "The worker works 8 hours a day."
                    : "This clause is revised to follow the cited section.";
    ObjectNode out = TestGateways.MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE").put("action", "REPLACE");
    out.put("proposed_text", text);
    out.put("rationale", "The revision follows the cited section and keeps the clause's purpose.");
    return out;
  }
}
