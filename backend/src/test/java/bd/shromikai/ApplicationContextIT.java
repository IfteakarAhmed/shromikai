package bd.shromikai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.EgressGuard;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.ai.fake.FakeEmbeddingPort;
import bd.shromikai.shared.ai.internal.FailClosedEgressGuard;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
@Requirement("FR-01")
class ApplicationContextIT {

  @Autowired JdbcClient jdbc;
  @Autowired ApplicationContext context;

  @Test
  void shouldApplyFlywayMigrations_whenContextStarts() {
    List<String> versions =
        jdbc.sql("select version from flyway_schema_history where success order by installed_rank")
            .query(String.class)
            .list();

    assertThat(versions).containsExactly("1", "2");
  }

  @Test
  void shouldHaveVectorExtension_whenContextStarts() {
    long count =
        jdbc.sql("select count(*) from pg_extension where extname = 'vector'")
            .query(Long.class)
            .single();

    assertThat(count).isEqualTo(1);
  }

  @Test
  void shouldAddFoundationColumns_whenV2Applied() {
    List<String> columns =
        jdbc.sql(
                """
                select column_name from information_schema.columns
                 where table_name = 'law_section' and column_name in ('content_hash', 'parser_version')
                 order by column_name
                """)
            .query(String.class)
            .list();

    assertThat(columns).containsExactly("content_hash", "parser_version");
  }

  @Test
  void shouldKeepAuditEventAppendOnly_whenRowIsUpdatedOrDeleted() {
    jdbc.sql("insert into audit_event (actor_kind, action) values ('SYSTEM', 'ADMIN_ACTION')")
        .update();

    assertThatThrownBy(() -> jdbc.sql("update audit_event set target_type = 'x'").update())
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.sql("delete from audit_event").update())
        .hasMessageContaining("younger than 12 months");
    assertThatThrownBy(() -> jdbc.sql("truncate audit_event").update())
        .hasMessageContaining("cannot be truncated");
  }

  @Test
  void shouldWireFakesAndFailClosedGuard_whenTestProfileIsActive() {
    assertThat(context.getBeansOfType(ChatPort.class).values())
        .singleElement()
        .isInstanceOf(FakeChatPort.class);
    assertThat(context.getBean(FakeEmbeddingPort.class)).isNotNull();
    assertThat(context.getBean(EgressGuard.class)).isInstanceOf(FailClosedEgressGuard.class);
  }
}
