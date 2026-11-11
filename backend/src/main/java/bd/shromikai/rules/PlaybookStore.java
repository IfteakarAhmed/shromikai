package bd.shromikai.rules;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Playbooks of an organisation in {@code playbook} and {@code playbook_rule} (FR-57, V7). At most
 * 10 active playbooks per organisation. Reads here do not check who asks; the API layer compares
 * the organisation and answers {@code 404} to a stranger (NFR-12, T-26). Rows hold the
 * organisation's own rules and messages: no contract text (R-02).
 */
@Repository
public class PlaybookStore {

  public static final int MAX_ACTIVE_PER_ORGANISATION = 10;

  private final JdbcClient jdbc;

  PlaybookStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public Playbook create(UUID orgId, @Nullable UUID createdBy, PlaybookInput input) {
    // Serialise creates per organisation so the limit cannot be overshot by a race.
    jdbc.sql("select id from organisation where id = :org for update")
        .param("org", orgId)
        .query(UUID.class)
        .optional()
        .orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
    long active =
        jdbc.sql("select count(*) from playbook where org_id = :org and archived_at is null")
            .param("org", orgId)
            .query(Long.class)
            .single();
    if (active >= MAX_ACTIVE_PER_ORGANISATION) {
      throw new ProblemException(ProblemType.PLAYBOOK_LIMIT_REACHED);
    }
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into playbook (id, org_id, name, side, created_by)
            values (:id, :org, :name, :side, :by)
            """)
        .param("id", id)
        .param("org", orgId)
        .param("name", input.name().strip())
        .param("side", input.side())
        .param("by", createdBy)
        .update();
    insertRules(id, input.rules());
    return find(id).orElseThrow();
  }

  @Transactional
  public Optional<Playbook> replace(UUID id, PlaybookInput input) {
    int updated =
        jdbc.sql(
                """
                update playbook set name = :name, side = :side, version = version + 1
                 where id = :id and archived_at is null
                """)
            .param("id", id)
            .param("name", input.name().strip())
            .param("side", input.side())
            .update();
    if (updated == 0) {
      return Optional.empty();
    }
    jdbc.sql("delete from playbook_rule where playbook_id = :id").param("id", id).update();
    insertRules(id, input.rules());
    return find(id);
  }

  /** Archives a playbook; returns whether an active one existed. */
  public boolean archive(UUID id) {
    return jdbc.sql(
                "update playbook set archived_at = now() where id = :id and archived_at is null")
            .param("id", id)
            .update()
        > 0;
  }

  /** An active playbook, or empty. */
  public Optional<Playbook> find(UUID id) {
    return jdbc.sql(
            """
            select id, org_id, name, side, version, created_at, archived_at
              from playbook where id = :id and archived_at is null
            """)
        .param("id", id)
        .query(
            (rs, n) ->
                new Playbook(
                    rs.getObject("id", UUID.class),
                    rs.getObject("org_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("side"),
                    rs.getInt("version"),
                    rs.getTimestamp("created_at").toInstant(),
                    null,
                    rules(rs.getObject("id", UUID.class))))
        .optional();
  }

  /** Active playbooks, newest first, of one organisation or of all when {@code orgId} is null. */
  public List<Playbook> list(@Nullable UUID orgId, int page, int size) {
    List<UUID> ids =
        jdbc.sql(
                """
                select id from playbook
                 where archived_at is null and (cast(:org as uuid) is null or org_id = :org)
                 order by created_at desc, id limit :size offset :offset
                """)
            .param("org", orgId)
            .param("size", size)
            .param("offset", (long) page * size)
            .query(UUID.class)
            .list();
    List<Playbook> out = new ArrayList<>();
    ids.forEach(id -> find(id).ifPresent(out::add));
    return out;
  }

  public long count(@Nullable UUID orgId) {
    return jdbc.sql(
            "select count(*) from playbook where archived_at is null"
                + " and (cast(:org as uuid) is null or org_id = :org)")
        .param("org", orgId)
        .query(Long.class)
        .single();
  }

  private void insertRules(UUID playbookId, List<PlaybookRule> rules) {
    for (int i = 0; i < rules.size(); i++) {
      PlaybookRule r = rules.get(i);
      jdbc.sql(
              """
              insert into playbook_rule (id, playbook_id, ordinal, clause_type, fact, comparator,
                                         value, unit, severity, message_bn, message_en,
                                         statute_section_key)
              values (:id, :pb, :ordinal, :clause, :fact, :comparator, :value, :unit, :severity,
                      :bn, :en, :statute)
              """)
          .param("id", UUID.randomUUID())
          .param("pb", playbookId)
          .param("ordinal", i)
          .param("clause", r.clauseType())
          .param("fact", r.fact().name())
          .param("comparator", r.comparator().name())
          .param("value", r.value())
          .param("unit", r.unit() == null ? null : r.unit().name())
          .param("severity", r.severity().name())
          .param("bn", r.messageBn())
          .param("en", r.messageEn())
          .param("statute", r.statuteSectionKey())
          .update();
    }
  }

  private List<PlaybookRule> rules(UUID playbookId) {
    return jdbc.sql(
            """
            select clause_type, fact, comparator, value, unit, severity, message_bn, message_en,
                   statute_section_key
              from playbook_rule where playbook_id = :id order by ordinal
            """)
        .param("id", playbookId)
        .query(
            (rs, n) ->
                new PlaybookRule(
                    rs.getString("clause_type"),
                    FactParameter.valueOf(rs.getString("fact")),
                    RuleComparator.valueOf(rs.getString("comparator")),
                    rs.getObject("value", Double.class),
                    rs.getString("unit") == null ? null : Unit.valueOf(rs.getString("unit")),
                    Severity.valueOf(rs.getString("severity")),
                    rs.getString("message_bn"),
                    rs.getString("message_en"),
                    rs.getString("statute_section_key")))
        .list();
  }
}
