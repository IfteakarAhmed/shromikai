package bd.shromikai.mcp.internal;

import bd.shromikai.analysis.AnalysisProperties;
import bd.shromikai.analysis.ClaimRedactionService;
import bd.shromikai.law.LawReadService;
import bd.shromikai.law.Section;
import bd.shromikai.law.SectionNumbers;
import bd.shromikai.shared.json.SnakeCase;
import bd.shromikai.shared.text.BanglaNormalizer;
import bd.shromikai.shared.text.OverlapChecker;
import bd.shromikai.verify.CitationOrigin;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.Verdict;
import bd.shromikai.verify.VerificationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code verify_legal_citation} (FR-64): the three checks of the verifier on an outside caller's
 * citation, origin {@code EXTERNAL} (no candidate set). The claim is redacted locally first
 * (architecture §10.7): if the local model is down the call fails with {@code
 * local-model-unavailable} and nothing is sent. The quote is checked against the official text
 * before it can reach the support model, so only text that matches the statute leaves the box.
 */
@Component
@Profile("mcp")
final class VerifyTool {

  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

  private final CitationVerifier verifier;
  private final ClaimRedactionService claims;
  private final LawReadService laws;
  private final AnalysisProperties analysis;
  private final ObjectMapper mapper;
  private final Clock clock;

  VerifyTool(
      CitationVerifier verifier,
      ClaimRedactionService claims,
      LawReadService laws,
      AnalysisProperties analysis,
      ObjectMapper mapper,
      Clock clock) {
    this.verifier = verifier;
    this.claims = claims;
    this.laws = laws;
    this.analysis = analysis;
    this.mapper = mapper;
    this.clock = clock;
  }

  McpCallGuard.Output verify(Args args) {
    String act = args.text("act_code", 2, 32);
    String number = args.sectionNumber("section_no");
    String quote = args.text("quoted_text", 20, 300);
    String claim = args.text("claim", 20, 600);

    String printed = BanglaNormalizer.toLatinDigits(number).strip();
    String latin = SectionNumbers.toLatin(printed).orElse(printed);
    Optional<Section> section = laws.section(act, latin, null);
    String key = section.map(Section::sectionKey).orElse(act + ":" + latin + "@current");
    String release = laws.corpusRelease() == null ? "none" : laws.corpusRelease();

    Verdict verdict;
    try (ClaimRedactionService.RedactedClaim redacted = claims.redact(claim)) {
      ClaimedCitation citation =
          ClaimedCitation.of(
              key,
              quote,
              redacted.text(),
              redacted.language(),
              CitationOrigin.EXTERNAL,
              null,
              null);
      VerificationContext context =
          new VerificationContext(
              clock.instant().atZone(DHAKA).toLocalDate(),
              analysis.analysis().jurisdictions(),
              release,
              Set.of(),
              1,
              redacted.context(),
              OverlapChecker.of());
      verdict = verifier.verify(citation, context);
    }

    ObjectNode out = mapper.createObjectNode();
    out.put("status", verdict.result().status().name());
    out.set("checks", SnakeCase.keys(mapper.valueToTree(verdict.result().checks())));
    ArrayNode reasons = out.putArray("reason_codes");
    verdict.result().reasonCodes().forEach(r -> reasons.add(r.name()));
    if (verdict.citation() == null) {
      out.putNull("verified_citation");
    } else {
      out.set("verified_citation", SnakeCase.keys(mapper.valueToTree(verdict.citation())));
    }
    out.put("corpus_release", verdict.result().corpusRelease());
    return new McpCallGuard.Output(out);
  }
}
