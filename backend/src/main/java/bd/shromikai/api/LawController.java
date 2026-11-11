package bd.shromikai.api;

import bd.shromikai.api.internal.SearchRateLimiter;
import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.law.Act;
import bd.shromikai.law.ActSummary;
import bd.shromikai.law.LawReadService;
import bd.shromikai.law.Section;
import bd.shromikai.retrieval.RetrievalResult;
import bd.shromikai.retrieval.RetrievalService;
import bd.shromikai.retrieval.SearchOptions;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-31 to E-34, the law endpoints (FR-22). Free-text search runs hybrid retrieval and graph
 * expansion locally on the query as written: no query rewriting and no cloud call, because a query
 * may contain personal data (ADR-0006). The query is never logged.
 */
@RestController
@RequestMapping("/api/v1/laws")
@Validated
public class LawController {

  private static final String JURISDICTION = "^(BD|ILO)$";

  private final LawReadService laws;
  private final RetrievalService retrieval;
  private final SearchRateLimiter rateLimiter;

  public LawController(
      LawReadService laws, RetrievalService retrieval, SearchRateLimiter rateLimiter) {
    this.laws = laws;
    this.retrieval = retrieval;
    this.rateLimiter = rateLimiter;
  }

  @Schema(name = "SearchResults")
  public record SearchResults(
      String query, @Nullable String corpusRelease, List<RetrievalResult> results) {}

  /** E-31 */
  @GetMapping
  public List<ActSummary> acts(
      @RequestParam(defaultValue = "BD")
          List<@Pattern(regexp = JURISDICTION) String> jurisdiction) {
    return laws.acts(jurisdiction);
  }

  /** E-32 */
  @GetMapping("/search")
  public SearchResults search(
      @AuthenticationPrincipal AuthenticatedSubject subject,
      @RequestParam @NotBlank @Size(max = 500) String q,
      @RequestParam(defaultValue = "any") @Pattern(regexp = "^(bn|en|any)$") String lang,
      @RequestParam(defaultValue = "8") @Min(1) @Max(20) int k,
      @RequestParam(defaultValue = "BD")
          List<@Pattern(regexp = JURISDICTION) String> jurisdiction) {
    rateLimiter.check(subject);
    // lang names the language of the query. bge-m3 shares one space across Bangla and English
    // and the text index holds both, so it does not filter (architecture §8).
    List<RetrievalResult> results =
        retrieval.search(q, SearchOptions.freeText(k, Set.copyOf(jurisdiction))).results();
    return new SearchResults(q, laws.corpusRelease(), results);
  }

  /** E-33 */
  @GetMapping("/{act}")
  public Act act(@PathVariable("act") String actCode) {
    return laws.act(actCode).orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
  }

  /** E-34 */
  @GetMapping("/{act}/{section}")
  public Section section(
      @PathVariable("act") String actCode,
      @PathVariable("section") String sectionNo,
      @RequestParam(required = false) @Nullable @Size(max = 32) String version) {
    return laws.section(actCode, sectionNo, version)
        .orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
  }
}
