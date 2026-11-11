package bd.shromikai.verify;

import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.trace.ActorKind;
import bd.shromikai.trace.AuditAction;
import bd.shromikai.trace.AuditEvent;
import bd.shromikai.trace.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The human review queue and the worker's review tickets (FR-50, FR-60, FR-61). An admin claims an
 * item, then approves it with a citation (existence and quote are checked first; the reviewer
 * counts as the support check), approves it without one, or rejects it; every decision is audited.
 * A worker follows a ticket with its secret token. Items hold no contract text (R-02). A decided
 * item lives 30 days from its decision (architecture §14), a ticket until its last item expires.
 */
@Service
public class ReviewDesk {

  /** What a PATCH may carry (api.md {@code ReviewItemPatch}). */
  public record Patch(
      @Nullable String status,
      @Nullable String decision,
      @Nullable String sectionKey,
      @Nullable String quote,
      @Nullable String claim,
      @Nullable String note) {}

  /** List filters; any may be {@code null}. */
  public record Filter(
      @Nullable String status,
      @Nullable String kind,
      @Nullable String clauseType,
      @Nullable String severity) {}

  private static final Set<String> JURISDICTIONS = Set.of("BD", "ILO");
  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

  private final JdbcClient jdbc;
  private final CitationVerifier verifier;
  private final AuditService audit;
  private final ObjectMapper mapper;
  private final Clock clock;

  public ReviewDesk(
      JdbcClient jdbc,
      CitationVerifier verifier,
      AuditService audit,
      ObjectMapper mapper,
      Clock clock) {
    this.jdbc = jdbc;
    this.verifier = verifier;
    this.audit = audit;
    this.mapper = mapper;
    this.clock = clock;
  }

  // ---------------------------------------------------------------------------- admin queue

  public ObjectNode page(Filter filter, int page, int size) {
    String where =
        " where (cast(:status as text) is null or status = :status)"
            + " and (cast(:kind as text) is null or kind = :kind)"
            + " and (cast(:clause as text) is null or clause_type = :clause)"
            + " and (cast(:severity as text) is null or severity = :severity)";
    long total =
        jdbc.sql("select count(*) from review_item" + where)
            .param("status", filter.status())
            .param("kind", filter.kind())
            .param("clause", filter.clauseType())
            .param("severity", filter.severity())
            .query(Long.class)
            .single();
    List<ObjectNode> rows =
        jdbc.sql(
                "select * from review_item"
                    + where
                    + " order by created_at, id limit :size offset :offset")
            .param("status", filter.status())
            .param("kind", filter.kind())
            .param("clause", filter.clauseType())
            .param("severity", filter.severity())
            .param("size", size)
            .param("offset", (long) page * size)
            .query((rs, n) -> view(rs))
            .list();
    ObjectNode out = mapper.createObjectNode();
    ArrayNode items = out.putArray("items");
    rows.forEach(items::add);
    out.put("page", page);
    out.put("size", size);
    out.put("totalItems", total);
    return out;
  }

  public Optional<ObjectNode> get(UUID id) {
    return jdbc.sql("select * from review_item where id = :id")
        .param("id", id)
        .query((rs, n) -> view(rs))
        .optional();
  }

  /** Claims, releases, approves or rejects an item for an admin; audited. */
  @Transactional
  public ObjectNode patch(UUID id, UUID adminId, Patch patch) {
    Row row =
        jdbc.sql("select status, assignee_id, kind from review_item where id = :id for update")
            .param("id", id)
            .query(
                (rs, n) ->
                    new Row(
                        rs.getString("status"),
                        rs.getObject("assignee_id", UUID.class),
                        rs.getString("kind")))
            .optional()
            .orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
    Instant now = clock.instant();
    if (patch.status() != null && patch.decision() == null) {
      switch (patch.status()) {
        case "IN_REVIEW" -> claim(id, adminId, row);
        case "OPEN" -> release(id, row);
        default -> throw new ProblemException(ProblemType.INVALID_TRANSITION);
      }
      audit(adminId, id, "IN_REVIEW".equals(patch.status()) ? "claimed" : "released");
      return get(id).orElseThrow();
    }
    if (patch.decision() == null || patch.status() != null) {
      throw new ProblemException(ProblemType.VALIDATION_FAILED, "Send a status or a decision");
    }
    decide(id, adminId, row, patch, now);
    audit(adminId, id, patch.decision().toLowerCase());
    return get(id).orElseThrow();
  }

  private record Row(String status, @Nullable UUID assignee, String kind) {}

  private void claim(UUID id, UUID adminId, Row row) {
    if ("IN_REVIEW".equals(row.status())) {
      if (adminId.equals(row.assignee())) {
        return; // claiming again is harmless
      }
      throw new ProblemException(ProblemType.ALREADY_CLAIMED);
    }
    if (!"OPEN".equals(row.status())) {
      throw new ProblemException(
          isDecided(row.status()) ? ProblemType.ALREADY_DECIDED : ProblemType.INVALID_TRANSITION);
    }
    jdbc.sql("update review_item set status = 'IN_REVIEW', assignee_id = :admin where id = :id")
        .param("admin", adminId)
        .param("id", id)
        .update();
  }

  private void release(UUID id, Row row) {
    if (!"IN_REVIEW".equals(row.status())) {
      throw new ProblemException(
          isDecided(row.status()) ? ProblemType.ALREADY_DECIDED : ProblemType.INVALID_TRANSITION);
    }
    jdbc.sql("update review_item set status = 'OPEN', assignee_id = null where id = :id")
        .param("id", id)
        .update();
  }

  private void decide(UUID id, UUID adminId, Row row, Patch patch, Instant now) {
    if (isDecided(row.status())) {
      throw new ProblemException(ProblemType.ALREADY_DECIDED);
    }
    if (!"IN_REVIEW".equals(row.status())) {
      throw new ProblemException(ProblemType.INVALID_TRANSITION);
    }
    if (!adminId.equals(row.assignee())) {
      throw new ProblemException(ProblemType.ALREADY_CLAIMED);
    }
    String decision = patch.decision();
    String sectionKey = null;
    String quote = null;
    switch (decision) {
      case "APPROVED_WITH_CITATION" -> {
        VerifiedCitation citation = verifyReviewerCitation(patch);
        sectionKey = citation.sectionKey();
        quote =
            citation.quote().length() > 300 ? citation.quote().substring(0, 300) : citation.quote();
      }
      case "APPROVED_WITHOUT_CITATION", "REJECTED" -> {
        if (patch.note() == null || patch.note().isBlank()) {
          throw new ProblemException(ProblemType.VALIDATION_FAILED, "A note is required");
        }
      }
      default -> throw new ProblemException(ProblemType.VALIDATION_FAILED, "Unknown decision");
    }
    if (patch.note() != null && patch.note().length() > 1000) {
      throw new ProblemException(ProblemType.VALIDATION_FAILED, "The note is too long");
    }
    Instant expires =
        "CORPUS_CHANGE".equals(row.kind())
            ? now.atZone(DHAKA).plusMonths(12).toInstant()
            : now.plus(java.time.Duration.ofDays(30));
    jdbc.sql(
            """
            update review_item
               set status = :status, decision = :decision, decision_section_key = :key,
                   decision_quote = :quote, decision_note = :note, decided_by = :admin,
                   decided_at = :at, expires_at = :expires
             where id = :id
            """)
        .param("status", "REJECTED".equals(decision) ? "REJECTED" : "APPROVED")
        .param("decision", decision)
        .param("key", sectionKey)
        .param("quote", quote)
        .param("note", patch.note())
        .param("admin", adminId)
        .param("at", Timestamp.from(now))
        .param("expires", Timestamp.from(expires))
        .param("id", id)
        .update();
  }

  /** The reviewer's citation must pass existence and quote checks before it can be saved. */
  private VerifiedCitation verifyReviewerCitation(Patch patch) {
    if (patch.sectionKey() == null || patch.quote() == null || patch.quote().length() < 20) {
      throw new ProblemException(
          ProblemType.VALIDATION_FAILED, "sectionKey and quote are required");
    }
    return reviewerCitation(patch.sectionKey(), patch.quote(), patch.claim());
  }

  private VerifiedCitation reviewerCitation(
      String sectionKey, String quote, @Nullable String claim) {
    ClaimedCitation claimed =
        ClaimedCitation.of(
            sectionKey,
            quote,
            bd.shromikai.shared.SensitiveText.of(claim == null ? "reviewer decision" : claim),
            "en",
            CitationOrigin.REVIEWER,
            null,
            null);
    Verdict verdict =
        verifier.verify(
            claimed,
            new VerificationContext(
                clock.instant().atZone(DHAKA).toLocalDate(), JURISDICTIONS, "none", Set.of(), 1));
    if (!verdict.verified()) {
      throw new ProblemException(ProblemType.CITATION_NOT_VERIFIABLE);
    }
    return verdict.citation();
  }

  private static boolean isDecided(String status) {
    return "APPROVED".equals(status) || "REJECTED".equals(status);
  }

  private void audit(UUID adminId, UUID itemId, String reason) {
    audit.record(
        AuditEvent.of(
                adminId, ActorKind.USER, AuditAction.REVIEW_DECISION, Map.of("reason", reason))
            .withTarget("review_item", itemId.toString()));
  }

  // ---------------------------------------------------------------------------- tickets

  /**
   * The status of the items behind a review ticket, for the worker who holds its token.
   *
   * @throws ProblemException {@code not-found} for a wrong token or ID, {@code ticket-expired} when
   *     the last item has expired
   */
  public ObjectNode ticket(UUID ticketId, @Nullable String token) {
    record TicketRow(
        String findingId,
        String summary,
        String status,
        @Nullable String decision,
        @Nullable String sectionKey,
        @Nullable String quote,
        @Nullable String note,
        @Nullable Instant decidedAt,
        Instant expiresAt,
        String tokenHash) {}
    List<TicketRow> rows =
        jdbc.sql(
                """
                select finding_id, risk_summary, status, decision, decision_section_key,
                       decision_quote, decision_note, decided_at, expires_at, ticket_token_sha256
                  from review_item where ticket_id = :id order by created_at, id
                """)
            .param("id", ticketId)
            .query(
                (rs, n) ->
                    new TicketRow(
                        rs.getString("finding_id"),
                        rs.getString("risk_summary"),
                        rs.getString("status"),
                        rs.getString("decision"),
                        rs.getString("decision_section_key"),
                        rs.getString("decision_quote"),
                        rs.getString("decision_note"),
                        instant(rs, "decided_at"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getString("ticket_token_sha256")))
            .list();
    if (rows.isEmpty() || token == null || !sameHash(rows.get(0).tokenHash(), token)) {
      throw new ProblemException(ProblemType.NOT_FOUND);
    }
    Instant last = rows.stream().map(TicketRow::expiresAt).max(Instant::compareTo).orElseThrow();
    if (!clock.instant().isBefore(last)) {
      throw new ProblemException(ProblemType.TICKET_EXPIRED);
    }
    ObjectNode out = mapper.createObjectNode();
    out.put("ticketId", ticketId.toString());
    out.put("expiresAt", last.toString());
    ArrayNode items = out.putArray("items");
    for (TicketRow row : rows) {
      ObjectNode item = items.addObject();
      item.put("findingId", row.findingId());
      item.put("title", row.summary());
      item.put("status", row.status());
      item.set("decision", mapper.valueToTree(row.decision()));
      if (row.sectionKey() != null && row.quote() != null) {
        try {
          item.set(
              "citation",
              mapper.valueToTree(reviewerCitation(row.sectionKey(), row.quote(), null)));
        } catch (ProblemException stale) {
          item.putNull(
              "citation"); // the corpus changed since: show no citation rather than an old one
        }
      } else {
        item.putNull("citation");
      }
      item.set("note", mapper.valueToTree(row.note()));
      item.set(
          "decidedAt",
          mapper.valueToTree(row.decidedAt() == null ? null : row.decidedAt().toString()));
    }
    return out;
  }

  private static boolean sameHash(String storedHex, String token) {
    return MessageDigest.isEqual(
        storedHex.getBytes(StandardCharsets.UTF_8),
        Hashing.sha256Hex(token).getBytes(StandardCharsets.UTF_8));
  }

  // ---------------------------------------------------------------------------- views

  private ObjectNode view(ResultSet rs) throws SQLException {
    ObjectNode node = mapper.createObjectNode();
    node.put("id", rs.getString("id"));
    node.put("kind", rs.getString("kind"));
    node.set("analysisId", mapper.valueToTree(rs.getString("analysis_id")));
    node.set("findingId", mapper.valueToTree(rs.getString("finding_id")));
    node.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
    node.put("expiresAt", rs.getTimestamp("expires_at").toInstant().toString());
    node.put("status", rs.getString("status"));
    node.set("clauseType", mapper.valueToTree(rs.getString("clause_type")));
    node.set("findingKind", mapper.valueToTree(rs.getString("finding_kind")));
    node.set("severity", mapper.valueToTree(rs.getString("severity")));
    node.set("side", mapper.valueToTree(rs.getString("side")));
    node.set("riskSummary", mapper.valueToTree(rs.getString("risk_summary")));
    node.set("facts", json(rs.getString("facts"), mapper.createArrayNode()));
    if (rs.getString("claimed_section_key") == null) {
      node.putNull("claimedCitation");
    } else {
      ObjectNode claimed = node.putObject("claimedCitation");
      claimed.put("sectionKey", rs.getString("claimed_section_key"));
      claimed.put(
          "quote", rs.getString("claimed_quote") == null ? "" : rs.getString("claimed_quote"));
      claimed.put(
          "claim", rs.getString("claimed_claim") == null ? "" : rs.getString("claimed_claim"));
      claimed.put("claimReduced", rs.getBoolean("claim_reduced"));
    }
    node.putNull("verification");
    node.set("reasonCodes", mapper.valueToTree(array(rs, "reason_codes")));
    node.set("candidateSectionKeys", mapper.valueToTree(array(rs, "candidate_section_keys")));
    node.set("corpusChange", json(rs.getString("corpus_change"), mapper.nullNode()));
    node.set("assigneeId", mapper.valueToTree(rs.getString("assignee_id")));
    if (rs.getString("decision") == null) {
      node.putNull("decision");
    } else {
      ObjectNode decision = node.putObject("decision");
      decision.put("decision", rs.getString("decision"));
      String key = rs.getString("decision_section_key");
      String quote = rs.getString("decision_quote");
      if (key != null && quote != null) {
        try {
          decision.set("citation", mapper.valueToTree(reviewerCitation(key, quote, null)));
        } catch (ProblemException stale) {
          decision.putNull("citation");
        }
      } else {
        decision.putNull("citation");
      }
      decision.set("note", mapper.valueToTree(rs.getString("decision_note")));
      decision.put("decidedBy", rs.getString("decided_by"));
      decision.put("decidedAt", rs.getTimestamp("decided_at").toInstant().toString());
    }
    return node;
  }

  private JsonNode json(@Nullable String text, JsonNode fallback) {
    if (text == null) {
      return fallback;
    }
    try {
      return mapper.readTree(text);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return fallback;
    }
  }

  private static List<String> array(ResultSet rs, String column) throws SQLException {
    java.sql.Array array = rs.getArray(column);
    if (array == null) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (Object value : (Object[]) array.getArray()) {
      out.add(String.valueOf(value));
    }
    return out;
  }

  private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant();
  }
}
