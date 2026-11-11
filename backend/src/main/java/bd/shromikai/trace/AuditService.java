package bd.shromikai.trace;

import bd.shromikai.shared.json.JsonSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Appends rows to the append-only {@code audit_event} table (FR-10, ADR-0009). The detail is
 * validated against a closed schema first, so a caller cannot slip a token or an address into the
 * trail by accident.
 */
@Service
public class AuditService {

  static final String DETAIL_SCHEMA = "schemas/audit/audit-detail.v1.schema.json";

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;
  private final JsonSchemas schemas = new JsonSchemas();

  public AuditService(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void record(AuditEvent event) {
    JsonNode detail = mapper.valueToTree(event.detail());
    List<String> errors = schemas.validate(DETAIL_SCHEMA, detail);
    if (!errors.isEmpty()) {
      // The message names schema violations only; it never echoes the rejected values.
      throw new IllegalArgumentException("Audit detail violates its schema: " + errors.size());
    }
    String target = event.targetId();
    if (target != null && target.length() > 200) {
      target = target.substring(0, 200);
    }
    jdbc.sql(
            """
            insert into audit_event (actor_id, actor_kind, action, target_type, target_id, detail)
            values (cast(:actor as uuid), :kind, :action, cast(:targetType as text), cast(:targetId as text),
                   cast(:detail as jsonb))
            """)
        .param("actor", event.actorId())
        .param("kind", event.actorKind().name())
        .param("action", event.action().name())
        .param("targetType", event.targetType())
        .param("targetId", target)
        .param("detail", detail.toString())
        .update();
  }
}
