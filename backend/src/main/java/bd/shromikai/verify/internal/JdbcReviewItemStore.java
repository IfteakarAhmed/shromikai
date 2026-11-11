package bd.shromikai.verify.internal;

import bd.shromikai.verify.FactView;
import bd.shromikai.verify.ReviewItem;
import bd.shromikai.verify.ReviewItemStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@link ReviewItemStore} over the {@code review_item} table (V6). Metadata only (R-02). */
@Repository
class JdbcReviewItemStore implements ReviewItemStore {

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  JdbcReviewItemStore(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public void save(ReviewItem item) {
    jdbc.sql(
            """
            insert into review_item (id, kind, analysis_id, finding_id, clause_type, finding_kind,
                                     severity, side, risk_summary, facts, claimed_section_key,
                                     claimed_quote, claimed_claim, claim_reduced, reason_codes,
                                     candidate_section_keys, status, created_at, expires_at)
            values (:id, 'FINDING', :analysis, :finding, :clauseType, :kind, :severity, :side,
                    :summary, cast(:facts as jsonb), :sectionKey, :quote, :claim, :reduced,
                    cast(:reasons as text[]), cast(:candidates as text[]), 'OPEN', :created,
                    :expires)
            """)
        .param("id", item.id())
        .param("analysis", item.analysisId())
        .param("finding", item.findingId())
        .param("clauseType", item.clauseType())
        .param("kind", item.findingKind())
        .param("severity", item.severity())
        .param("side", item.side())
        .param("summary", item.riskSummary())
        .param("facts", facts(item.facts()))
        .param("sectionKey", item.claimedSectionKey())
        .param("quote", item.claimedQuote())
        .param("claim", item.claimedClaim())
        .param("reduced", item.claimReduced())
        .param("reasons", array(item.reasonCodes()))
        .param("candidates", array(item.candidateSectionKeys()))
        .param("created", Timestamp.from(item.createdAt()))
        .param("expires", Timestamp.from(item.expiresAt()))
        .update();
  }

  @Override
  public void attachTicket(java.util.UUID analysisId, java.util.UUID ticketId, String tokenSha256) {
    jdbc.sql(
            """
            update review_item set ticket_id = :ticket, ticket_token_sha256 = :hash
             where analysis_id = :analysis and kind = 'FINDING'
            """)
        .param("ticket", ticketId)
        .param("hash", tokenSha256)
        .param("analysis", analysisId)
        .update();
  }

  private String facts(List<FactView> facts) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (FactView fact : facts) {
      Map<String, Object> row = new java.util.LinkedHashMap<>();
      row.put("parameter", fact.parameter());
      row.put("value", fact.value());
      row.put("unit", fact.unit());
      rows.add(row);
    }
    try {
      return mapper.writeValueAsString(rows);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /** A PostgreSQL array literal of plain identifiers (codes and section keys). */
  private static String array(List<String> values) {
    StringBuilder out = new StringBuilder("{");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append('"').append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    }
    return out.append('}').toString();
  }
}
