package bd.shromikai.api;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.IdentityFixtures;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * FR-73, KPI-12 to KPI-14: the admin usage report. The KPI block equals hand-computed values on
 * seeded rows and holds null values when there is no data; only an admin may read it.
 */
@IntegrationTest
@Requirement("FR-73")
class AdminUsageIT {

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder encoder;

  private UUID orgId;
  private UUID adminId;
  private static final JsonSchemas SCHEMAS = new JsonSchemas();

  @BeforeEach
  void anOrganisationAndAnAdmin() {
    orgId =
        jdbc.sql(
                "insert into organisation (name, kind, plan) values ('Usage Org', 'HR', 'TEAM')"
                    + " returning id")
            .query(UUID.class)
            .single();
    adminId =
        IdentityFixtures.insertUser(
            jdbc,
            encoder,
            "usage-admin-" + UUID.randomUUID() + "@example.test",
            IdentityFixtures.PASSWORD,
            Role.ADMIN,
            false);
  }

  @AfterEach
  void clean() {
    jdbc.sql("delete from analysis_run where org_id = :o").param("o", orgId).update();
    jdbc.sql("delete from usage_event where org_id = :o").param("o", orgId).update();
    jdbc.sql("delete from organisation where id = :o").param("o", orgId).update();
    jdbc.sql("delete from app_user where email like 'usage-admin-%'").update();
  }

  private UUID run(String status, String errorCode, long costMicros) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into analysis_run (id, subject_kind, subject_hash, org_id, client_kind, side,
                                      output_language, input_kind, page_count, status, error_code,
                                      pipeline_version, cost_usd_micros, created_at)
            values (:id, 'USER', repeat('e', 64), :org, 'API', 'WORKER', 'bn', 'TEXT', 1, :status,
                    :error, 'p7-1', :cost, now())
            """)
        .param("id", id)
        .param("org", orgId)
        .param("status", status)
        .param("error", errorCode)
        .param("cost", costMicros)
        .update();
    return id;
  }

  private void usage(String kind, long costMicros) {
    jdbc.sql(
            "insert into usage_event (org_id, kind, units, cost_usd_micros) values (:o, :k, 1, :c)")
        .param("o", orgId)
        .param("k", kind)
        .param("c", costMicros)
        .update();
  }

  private void trace(UUID analysis, String stage, String model, long costMicros) {
    jdbc.sql(
            """
            insert into analysis_trace (analysis_id, step, detail, model_id, cost_usd_micros)
            values (:a, :s, '{}'::jsonb, :m, :c)
            """)
        .param("a", analysis)
        .param("s", stage)
        .param("m", model)
        .param("c", costMicros)
        .update();
  }

  private void decidedReview(UUID analysis, String finding, int hours) {
    jdbc.sql(
            """
            insert into review_item (id, kind, analysis_id, finding_id, clause_type, finding_kind,
                                     severity, side, risk_summary, facts, reason_codes, status,
                                     decision, decided_by, decided_at, created_at, expires_at)
            values (gen_random_uuid(), 'FINDING', :a, :f, 'TERMINATION', 'CONFLICT', 'HIGH',
                    'WORKER', 'summary', '[]'::jsonb, array['SUPPORT_PARTIAL'], 'REJECTED',
                    'REJECTED', :admin, now(), now() - make_interval(hours => :h),
                    now() + interval '30 days')
            """)
        .param("a", analysis)
        .param("f", finding)
        .param("admin", adminId)
        .param("h", hours)
        .update();
  }

  private ResponseEntity<JsonNode> usageReport(String query, Role role) {
    String token =
        jwt.issueUser(role == Role.ADMIN ? adminId : UUID.randomUUID(), role, null, false).value();
    return http.exchange(
        "/api/v1/admin/usage" + query,
        HttpMethod.GET,
        new HttpEntity<>(bearer(token)),
        JsonNode.class);
  }

  private String window() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Dhaka"));
    return "?orgId=" + orgId + "&from=" + today.minusDays(6) + "&to=" + today;
  }

  @Test
  void theKpisEqualTheHandComputedValues() {
    UUID ok1 = run("SUCCEEDED", null, 100_000);
    UUID ok2 = run("SUCCEEDED", null, 200_000);
    UUID ok3 = run("SUCCEEDED", null, 300_000);
    run("FAILED", "analysis-timeout", 0);
    run("FAILED", "not-an-employment-contract", 0); // excluded from KPI-12
    run("FAILED", "unreadable-document", 0); // excluded from KPI-12
    usage("ANALYSIS", 100_000);
    usage("ANALYSIS", 200_000);
    usage("ANALYSIS", 300_000);
    usage("COMPARE", 50_000);
    usage("MEMO_RENDER", 0);
    usage("MEMO_RENDER", 0);
    usage("MCP_CALL", 10_000);
    trace(ok1, "RISK", "model-a", 60_000);
    trace(ok1, "VERIFY", "model-b", 40_000);
    trace(ok2, "RISK", "model-a", 200_000);
    decidedReview(ok1, "f1", 10);
    decidedReview(ok2, "f2", 30);

    ResponseEntity<JsonNode> response = usageReport(window(), Role.ADMIN);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode report = response.getBody();
    assertThat(SCHEMAS.validate("schemas/api/usage-report.v1.schema.json", report)).isEmpty();
    JsonNode kpis = report.path("kpis");
    assertThat(kpis.path("windowDays").asInt()).isEqualTo(7);
    assertThat(kpis.at("/completionRate/count").asInt()).isEqualTo(3);
    assertThat(kpis.at("/completionRate/n").asInt()).isEqualTo(4);
    assertThat(kpis.at("/completionRate/value").asDouble()).isEqualTo(0.75);
    assertThat(kpis.at("/memoDownloadRate/count").asInt()).isEqualTo(2);
    assertThat(kpis.at("/memoDownloadRate/n").asInt()).isEqualTo(3);
    assertThat(kpis.at("/memoDownloadRate/value").asDouble()).isCloseTo(2.0 / 3, offset(1e-6));
    assertThat(kpis.at("/reviewTurnaroundHoursMedian/n").asInt()).isEqualTo(2);
    assertThat(kpis.at("/reviewTurnaroundHoursMedian/value").asDouble())
        .isCloseTo(20.0, offset(0.01));
    assertThat(report.at("/totals/analyses").asInt()).isEqualTo(3);
    assertThat(report.at("/totals/compares").asInt()).isEqualTo(1);
    assertThat(report.at("/totals/memoRenders").asInt()).isEqualTo(2);
    assertThat(report.at("/totals/mcpCalls").asInt()).isEqualTo(1);
    assertThat(report.at("/totals/costUsd").asDouble()).isCloseTo(0.66, offset(1e-6));
    assertThat(report.at("/totals/costByModel/model-a").asDouble()).isCloseTo(0.26, offset(1e-6));
    assertThat(report.at("/totals/costByModel/model-b").asDouble()).isCloseTo(0.04, offset(1e-6));
    assertThat(report.path("days")).hasSize(7);
    JsonNode today = report.path("days").get(6);
    assertThat(today.path("analyses").asInt()).isEqualTo(3);
    assertThat(today.path("date").asText())
        .isEqualTo(LocalDate.now(ZoneId.of("Asia/Dhaka")).toString());
  }

  @Test
  void withNoDataEveryKpiValueIsNullAndTheDaysAreZero() {
    ResponseEntity<JsonNode> response = usageReport(window(), Role.ADMIN);

    JsonNode report = response.getBody();
    assertThat(SCHEMAS.validate("schemas/api/usage-report.v1.schema.json", report)).isEmpty();
    for (String kpi :
        new String[] {"completionRate", "memoDownloadRate", "reviewTurnaroundHoursMedian"}) {
      assertThat(report.at("/kpis/" + kpi + "/value").isNull()).as(kpi).isTrue();
      assertThat(report.at("/kpis/" + kpi + "/n").asInt()).as(kpi).isZero();
    }
    assertThat(report.path("days")).hasSize(7);
    assertThat(report.at("/totals/analyses").asInt()).isZero();
  }

  @Test
  void aWindowOverNinetyTwoDaysOrBackwardsIs400() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Dhaka"));

    assertThat(
            usageReport("?from=" + today.minusDays(100) + "&to=" + today, Role.ADMIN)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            usageReport("?from=" + today + "&to=" + today.minusDays(2), Role.ADMIN).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aWorkerGets403() {
    assertThat(usageReport(window(), Role.WORKER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
