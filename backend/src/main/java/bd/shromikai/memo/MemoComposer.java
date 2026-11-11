package bd.shromikai.memo;

import bd.shromikai.memo.MemoInput.FindingView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Assembles the memo (api.md §5.11, FR-51). Only the summary and the next steps come from a model;
 * every other section is built from the structured result with no model call. A citation chip is
 * emitted only for a {@code VERIFIED} finding, and the composer refuses a finding that carries
 * citation IDs without being verified (R-01). All eight sections are always present.
 */
@Component
public final class MemoComposer {

  private final ObjectMapper mapper;

  public MemoComposer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public ObjectNode compose(MemoInput in) {
    MemoStrings s = new MemoStrings(in.language());
    for (FindingView f : in.findings()) {
      if (!"VERIFIED".equals(f.verificationStatus()) && !f.citationIds().isEmpty()) {
        throw new IllegalArgumentException(
            "Finding " + f.id() + " has citations but is not VERIFIED");
      }
    }
    ObjectNode memo = mapper.createObjectNode();
    memo.put("id", in.id().toString());
    memo.put("analysisId", in.analysisId().toString());
    memo.put("language", in.language());
    memo.put("readingLevel", in.readingLevel());
    memo.put("title", s.title());
    memo.put("generatedAt", in.generatedAt().toString());
    ArrayNode sections = memo.putArray("sections");

    section(sections, "SUMMARY", s.summaryHeading()).add(paragraph(in.summary()));

    ArrayNode table = section(sections, "RISK_TABLE", s.riskTableHeading());
    ObjectNode riskTable = table.addObject();
    riskTable.put("type", "riskTable");
    ArrayNode ids = riskTable.putArray("findingIds");
    in.findings().forEach(f -> ids.add(f.id()));

    ArrayNode details = section(sections, "RISK_DETAILS", s.detailsHeading());
    for (FindingView f : in.findings()) {
      ObjectNode ref = details.addObject();
      ref.put("type", "findingRef");
      ref.put("findingId", f.id());
      details.add(paragraph(f.explanation()));
      if (!f.whyItMatters().isBlank()) {
        details.add(paragraph(f.whyItMatters()));
      }
      if ("POLICY_ONLY".equals(f.verificationStatus())) {
        details.add(paragraph(s.policyLabel()));
      } else if ("UNCERTAIN".equals(f.verificationStatus())) {
        details.add(paragraph(s.uncertainLabel()));
      }
      for (String citationId : f.citationIds()) {
        ObjectNode chip = details.addObject();
        chip.put("type", "citationChip");
        chip.put("citationId", citationId);
      }
    }

    ArrayNode revised = section(sections, "REVISED_CLAUSES", s.revisedHeading());
    if (in.redlineIds().isEmpty()) {
      revised.add(paragraph(s.noRedlines()));
    }
    for (String id : in.redlineIds()) {
      ObjectNode ref = revised.addObject();
      ref.put("type", "redlineRef");
      ref.put("redlineId", id);
    }

    ArrayNode obligations = section(sections, "OBLIGATIONS", s.obligationsHeading());
    if (in.obligationIds().isEmpty()) {
      obligations.add(paragraph(s.noObligations()));
    } else {
      ObjectNode obligationTable = obligations.addObject();
      obligationTable.put("type", "obligationTable");
      ArrayNode oids = obligationTable.putArray("obligationIds");
      in.obligationIds().forEach(oids::add);
    }

    ArrayNode next = section(sections, "NEXT_STEPS", s.nextStepsHeading());
    ObjectNode list = next.addObject();
    list.put("type", "list");
    ArrayNode items = list.putArray("items");
    in.nextSteps().forEach(items::add);

    MemoInput.Verification v = in.verification();
    section(sections, "VERIFICATION_AND_METHOD", s.methodHeading())
        .add(
            paragraph(
                s.method(
                    v.verifiedCitations(),
                    v.uncertainFindings(),
                    v.policyFindings(),
                    v.independence(),
                    in.footer().corpusRelease())));
    section(sections, "DISCLAIMER", s.disclaimerHeading()).add(paragraph(s.disclaimer()));

    ObjectNode summary = memo.putObject("verificationSummary");
    summary.put("verifiedCitations", v.verifiedCitations());
    summary.put("uncertainFindings", v.uncertainFindings());
    summary.put("policyFindings", v.policyFindings());
    summary.put("independence", v.independence());
    memo.put("disclaimer", s.disclaimer());
    ObjectNode footer = memo.putObject("footer");
    footer.put("analysisId", in.analysisId().toString());
    footer.put("corpusRelease", in.footer().corpusRelease());
    footer.put("pipelineVersion", in.footer().pipelineVersion());
    footer.put("keyId", in.footer().keyId());
    return memo;
  }

  /** The citation IDs a memo refers to through chips, for the contract check. */
  public static List<String> chipIds(ObjectNode memo) {
    List<String> out = new ArrayList<>();
    memo.path("sections")
        .forEach(
            section ->
                section
                    .path("blocks")
                    .forEach(
                        block -> {
                          if ("citationChip".equals(block.path("type").asText())) {
                            out.add(block.path("citationId").asText());
                          }
                        }));
    return out;
  }

  private ArrayNode section(ArrayNode sections, String kind, String heading) {
    ObjectNode section = sections.addObject();
    section.put("kind", kind);
    section.put("heading", heading);
    return section.putArray("blocks");
  }

  private ObjectNode paragraph(String text) {
    ObjectNode block = mapper.createObjectNode();
    block.put("type", "paragraph");
    block.put("text", text.length() > 2000 ? text.substring(0, 2000) : text);
    return block;
  }
}
