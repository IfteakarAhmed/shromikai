package bd.shromikai.analysis;

import bd.shromikai.rules.ClauseFact;
import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.FactView;
import bd.shromikai.verify.ReviewItem;
import bd.shromikai.verify.ReviewItemFactory;
import bd.shromikai.verify.ReviewItemFactory.Draft;
import bd.shromikai.verify.ReviewItemStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Opens a review item for every {@code UNCERTAIN} finding and for every rule/LLM severity conflict
 * (FR-49, FR-50). The item holds the clause type, kind, severity, typed facts, a redacted
 * paraphrase and the failed claim; the factory keeps them free of contract text. The paraphrase is
 * the finding's explanation; if it overlaps the source, the model's own "why it matters" sentence
 * is the one regeneration, and then the facts-only form.
 */
@Component
public final class ReviewQueue {

  public static final String RULE_LLM_CONFLICT = "RULE_LLM_CONFLICT";

  private final ReviewItemFactory factory;
  private final ReviewItemStore store;

  public ReviewQueue(ReviewItemFactory factory, ReviewItemStore store) {
    this.factory = factory;
    this.store = store;
  }

  /** The findings with {@code reviewItemId} set where an item was opened, and the items. */
  public record Opened(List<Finding> findings, List<ReviewItem> items, @Nullable Ticket ticket) {}

  /**
   * The ticket a worker follows to see the human decision (FR-61): its ID, the secret token (shown
   * to the owner only; the database keeps its SHA-256) and when the last item expires.
   */
  public record Ticket(UUID ticketId, String token, java.time.Instant expiresAt) {}

  public Opened open(
      UUID analysisId,
      Side side,
      List<Finding> findings,
      List<FindingMerge.Conflict> conflicts,
      Map<String, Clause> clauses,
      Map<String, List<ClauseFact>> facts,
      java.util.Set<String> candidateKeys) {
    List<Finding> out = new ArrayList<>();
    List<ReviewItem> items = new ArrayList<>();
    // Every clause of the document is a source for the overlap check: an OMISSION finding has no
    // clauses of its own and a model can copy from any clause (R-02, FR-50).
    List<SensitiveText> sources = clauses.values().stream().map(Clause::text).toList();
    for (Finding finding : findings) {
      boolean conflict = conflicts.stream().anyMatch(c -> c.findingId().equals(finding.id()));
      boolean uncertain = finding.verification() == FindingVerification.UNCERTAIN;
      if (!uncertain && !conflict) {
        out.add(finding);
        continue;
      }
      List<String> reasons = new ArrayList<>(finding.reasonCodes());
      if (conflict) {
        reasons.add(RULE_LLM_CONFLICT);
      }
      if (reasons.isEmpty()) {
        reasons.add("NOT_VERIFIED");
      }
      ClaimedCitation failed = finding.claims().isEmpty() ? null : finding.claims().getFirst();
      List<FactView> factViews = new ArrayList<>();
      for (String clauseId : finding.clauseIds()) {
        for (ClauseFact fact : facts.getOrDefault(clauseId, List.of())) {
          factViews.add(new FactView(fact.parameter().name(), fact.value(), fact.unit().name()));
        }
      }
      Draft draft =
          new Draft(
              analysisId,
              finding.id(),
              finding.clauseType(),
              finding.kind().name(),
              finding.severity().name(),
              side.name(),
              factViews,
              finding.explanation().reveal(),
              failed != null
                  ? failed.sectionKey()
                  : finding.citations().isEmpty()
                      ? null
                      : finding.citations().getFirst().sectionKey(),
              failed != null
                  ? failed.quote()
                  : finding.citations().isEmpty() ? null : finding.citations().getFirst().quote(),
              failed == null ? null : failed.claim(),
              reasons,
              candidateKeys.stream().sorted().toList(),
              sources);
      ReviewItem item = factory.create(draft, () -> Optional.of(finding.whyItMatters().reveal()));
      store.save(item);
      items.add(item);
      out.add(finding.withReviewItem(item.id()));
    }
    Ticket ticket = null;
    if (!items.isEmpty()) {
      byte[] secret = new byte[32];
      new java.security.SecureRandom().nextBytes(secret);
      String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
      UUID ticketId = UUID.randomUUID();
      store.attachTicket(analysisId, ticketId, Hashing.sha256Hex(token));
      ticket =
          new Ticket(
              ticketId,
              token,
              items.stream()
                  .map(ReviewItem::expiresAt)
                  .max(java.time.Instant::compareTo)
                  .orElseThrow());
    }
    return new Opened(out, items, ticket);
  }
}
