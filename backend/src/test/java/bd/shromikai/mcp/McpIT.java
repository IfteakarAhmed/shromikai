package bd.shromikai.mcp;

import bd.shromikai.auth.ApiKeyService;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.testsupport.FictionalCorpus;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.P6Stubs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base of the MCP integration tests: the real application on profiles {@code test} and {@code mcp},
 * the fictional corpus (R-10), scripted model stand-ins (not recorded model output) and helpers to
 * make organisations and keys.
 */
@IntegrationTest
@ActiveProfiles({"test", "mcp"})
abstract class McpIT {

  @Autowired protected TestRestTemplate http;
  @Autowired protected JdbcClient jdbc;
  @Autowired protected ApiKeyService keys;
  @Autowired protected FakeChatPort fake;
  @Autowired protected ObjectMapper mapper;
  @LocalServerPort protected int port;

  protected final java.util.List<UUID> orgs = new java.util.ArrayList<>();

  @BeforeEach
  void installStandIns() {
    FictionalCorpus.load(jdbc);
    fake.reset();
    P6Stubs.install(fake);
  }

  @AfterEach
  void cleanDatabase() throws InterruptedException {
    Thread.sleep(300);
    jdbc.sql("delete from usage_event").update();
    jdbc.sql("delete from api_key").update();
    for (UUID org : orgs) {
      jdbc.sql("delete from organisation where id = :o").param("o", org).update();
    }
    orgs.clear();
    jdbc.sql("delete from analysis_run").update();
    FictionalCorpus.clean(jdbc);
  }

  protected UUID organisation(String name) {
    UUID org =
        jdbc.sql(
                "insert into organisation (name, kind, plan) values (:n, 'NGO', 'API')"
                    + " returning id")
            .param("n", name)
            .query(UUID.class)
            .single();
    orgs.add(org);
    return org;
  }

  /** A key with the given scopes for a new organisation; returns the secret. */
  protected String key(String... scopes) {
    return keys.create(organisation("Org " + UUID.randomUUID()), "test", List.of(scopes), null)
        .key();
  }

  protected String keyFor(UUID org, String... scopes) {
    return keys.create(org, "test", List.of(scopes), null).key();
  }

  protected ResponseEntity<String> rpc(String key, String method, Map<String, Object> params) {
    HttpHeaders headers = new HttpHeaders();
    if (key != null) {
      headers.setBearerAuth(key);
    }
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
    Map<String, Object> body =
        Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params);
    return http.exchange("/mcp", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  protected ResponseEntity<String> initialize(String key) {
    return rpc(
        key,
        "initialize",
        Map.of(
            "protocolVersion",
            "2025-06-18",
            "capabilities",
            Map.of(),
            "clientInfo",
            Map.of("name", "test", "version", "1")));
  }

  protected JsonNode json(String body) throws Exception {
    return mapper.readTree(body);
  }

  protected String url() {
    return "http://localhost:" + port;
  }
}
