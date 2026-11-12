package bd.shromikai.mcp.internal;

import bd.shromikai.law.LawReadService;
import bd.shromikai.law.PackInfo;
import bd.shromikai.law.Section;
import bd.shromikai.law.SectionNumbers;
import bd.shromikai.retrieval.RetrievalResult;
import bd.shromikai.retrieval.RetrievalService;
import bd.shromikai.retrieval.SearchOptions;
import bd.shromikai.rules.RuleSet;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.shared.text.BanglaNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The read-only law tools: {@code search_bd_law}, {@code get_law_section}, {@code
 * list_jurisdiction_packs} and the markdown of the {@code law://bd/...} resource (FR-64). Search
 * runs on the local retrieval stack on the query as written: no rewriting and no cloud call, so a
 * query that holds personal data never leaves the box (architecture §10.7).
 */
@Component
@Profile("mcp")
final class LawTools {

  private static final Set<String> JURISDICTIONS = Set.of("BD", "ILO");

  private final RetrievalService retrieval;
  private final LawReadService laws;
  private final RuleSet rules;
  private final ObjectMapper mapper;

  LawTools(RetrievalService retrieval, LawReadService laws, RuleSet rules, ObjectMapper mapper) {
    this.retrieval = retrieval;
    this.laws = laws;
    this.rules = rules;
    this.mapper = mapper;
  }

  McpCallGuard.Output search(Args args) {
    String query = args.text("query", 1, 500);
    int topK = args.integer("top_k", 1, 20, 8);
    args.oneOf("language", Set.of("bn", "en", "any"), "any"); // validated; the index holds both
    List<RetrievalResult> found =
        retrieval.search(query, SearchOptions.freeText(topK, JURISDICTIONS)).results();
    ObjectNode out = mapper.createObjectNode();
    ArrayNode results = out.putArray("results");
    for (RetrievalResult r : found.stream().limit(topK).toList()) {
      ObjectNode node = results.addObject();
      node.put("section_key", r.sectionKey());
      node.put("act_code", r.actCode());
      node.put("section_no", r.sectionNo());
      node.put("heading_bn", r.headingBn());
      node.put("heading_en", r.headingEn());
      node.put("snippet", r.snippet());
      node.put("source_url", r.sourceUrl());
      node.put("fused_score", r.fusedScore());
      node.put("origin", r.origin().name());
    }
    out.put("corpus_release", laws.corpusRelease());
    return new McpCallGuard.Output(out);
  }

  McpCallGuard.Output section(Args args) {
    String act = args.text("act_code", 2, 32);
    String number = args.sectionNumber("section_no");
    Section section = find(act, number);
    ObjectNode out = mapper.createObjectNode();
    out.put("section_key", section.sectionKey());
    out.put("act_code", section.actCode());
    out.put("act_title_bn", section.actTitleBn());
    out.put("act_title_en", section.actTitleEn());
    out.put("section_no", section.sectionNo());
    out.put("section_no_original", section.sectionNoOriginal());
    out.put("heading_bn", section.headingBn());
    out.put("heading_en", section.headingEn());
    out.put("authoritative_lang", section.authoritativeLang());
    boolean bangla = "bn".equals(section.authoritativeLang());
    String other = section.translation() == null ? null : section.translation().text();
    out.put("text_bn", bangla ? section.authoritativeText() : other);
    out.put("text_en", bangla ? other : section.authoritativeText());
    if (section.translation() == null) {
      out.putNull("translation_official");
    } else {
      out.put("translation_official", section.translation().official());
    }
    out.put("version_label", section.versionLabel());
    out.put(
        "effective_from",
        section.effectiveFrom() == null ? null : section.effectiveFrom().toString());
    out.put("is_current", section.isCurrent());
    out.put("source_url", section.sourceUrl());
    out.put("fetched_at", section.fetchedAt().toString());
    ArrayNode related = out.putArray("related");
    section
        .related()
        .forEach(
            r ->
                related
                    .addObject()
                    .put("section_key", r.sectionKey())
                    .put("kind", r.kind())
                    .put("reviewed", r.reviewed()));
    out.put(
        "corpus_release",
        section.corpusRelease() == null ? laws.corpusRelease() : section.corpusRelease());
    return new McpCallGuard.Output(out);
  }

  McpCallGuard.Output packs() {
    ObjectNode out = mapper.createObjectNode();
    ArrayNode packs = out.putArray("packs");
    long active = rules.active().size();
    for (PackInfo pack : laws.packs()) {
      ObjectNode node = packs.addObject();
      node.put("code", pack.code());
      node.put("name", pack.nameEn());
      node.put("name_bn", pack.nameBn());
      ArrayNode acts = node.putArray("acts");
      pack.acts()
          .forEach(
              a ->
                  acts.addObject()
                      .put("act_code", a.actCode())
                      .put("title_en", a.titleEn())
                      .put("title_bn", a.titleBn())
                      .put("section_count", a.sectionCount())
                      .put("authoritative_lang", a.authoritativeLang()));
      ArrayNode ilo = node.putArray("ilo_conventions");
      pack.iloConventions()
          .forEach(
              a ->
                  ilo.addObject()
                      .put("code", a.actCode())
                      .put("ratification_status", a.ratificationStatus()));
      node.put("active_rules", active);
      node.put("reviewed_ilo_mappings", pack.reviewedIloMappings());
      node.put("corpus_release", laws.corpusRelease());
    }
    out.put("corpus_release", laws.corpusRelease());
    return new McpCallGuard.Output(out);
  }

  /** The {@code text/markdown} of {@code law://bd/{act_code}/{section_no}}. */
  String markdown(String act, String number) {
    Section s = find(act, number);
    StringBuilder md = new StringBuilder();
    String heading = s.headingEn() != null ? s.headingEn() : s.headingBn();
    md.append("# ").append(s.actTitleEn()).append(", section ").append(s.sectionNo());
    if (heading != null) {
      md.append(": ").append(heading);
    }
    md.append("\n\n## Authoritative text (").append(s.authoritativeLang()).append(")\n\n");
    md.append(s.authoritativeText()).append("\n");
    if (s.translation() != null) {
      md.append("\n## Translation (")
          .append(s.translation().lang())
          .append(", ")
          .append(s.translation().official() ? "official" : "unofficial")
          .append(")\n\n")
          .append(s.translation().text())
          .append("\n");
    }
    md.append("\n- Section key: ").append(s.sectionKey());
    md.append("\n- Version: ").append(s.versionLabel());
    md.append("\n- Effective from: ")
        .append(s.effectiveFrom() == null ? "not stated" : s.effectiveFrom().toString());
    md.append("\n- Current: ").append(s.isCurrent() ? "yes" : "no");
    md.append("\n- Source: ").append(s.sourceUrl());
    md.append("\n\n").append(McpCallGuard.DISCLAIMER).append("\n");
    return md.toString();
  }

  /** The current section of an act; {@code not-found} when there is none. */
  Section find(String act, String number) {
    String printed = BanglaNormalizer.toLatinDigits(number).strip();
    String latin = SectionNumbers.toLatin(printed).orElse(printed);
    return laws.section(act, latin, null)
        .orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
  }
}
