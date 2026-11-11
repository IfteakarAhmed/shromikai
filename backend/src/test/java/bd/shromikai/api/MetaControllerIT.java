package bd.shromikai.api;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
@Requirement("FR-01")
class MetaControllerIT {

  @Autowired TestRestTemplate http;
  @Autowired JdbcClient jdbc;
  @Autowired ShromikaiProperties properties;

  private final JsonSchemas schemas = new JsonSchemas();

  @BeforeEach
  void seedFictionalCorpus() {
    // Fictional pack xx (R-10): two invented sections, one superseded, one embedded.
    long actId =
        jdbc.sql(
                """
                insert into law_act (code, title_en, jurisdiction, source_url)
                values ('XX-ACT-1', 'Fictional Act (test only)', 'XX', 'https://example.invalid/xx')
                returning id
                """)
            .query(Long.class)
            .single();
    jdbc.sql(
            """
            insert into law_section (act_id, section_no, text_en, version_label, is_current, source_url, embedding, section_key)
            values (:act, '1', 'Fictional text one.', 'original', false, 'https://example.invalid/xx/1', null,
                    'XX-ACT-1:1@original'),
                   (:act, '1', 'Fictional text one, amended.', 'amend-1', true, 'https://example.invalid/xx/1',
                    array_fill(0.01::real, array[1024])::vector, 'XX-ACT-1:1@amend-1')
            """)
        .param("act", actId)
        .update();
  }

  @AfterEach
  void clearCorpus() {
    jdbc.sql("delete from law_act where code = 'XX-ACT-1'").update();
  }

  @Test
  void shouldReturnCountsAndModelIds_whenMetaIsRequested() {
    ResponseEntity<JsonNode> response = http.getForEntity("/api/v1/meta", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode meta = response.getBody();
    assertThat(schemas.validate("schemas/api/meta.schema.json", meta)).isEmpty();
    assertThat(meta.at("/corpus/acts").asLong()).isEqualTo(1);
    assertThat(meta.at("/corpus/sections").asLong()).isEqualTo(2);
    assertThat(meta.at("/corpus/currentSections").asLong()).isEqualTo(1);
    assertThat(meta.at("/corpus/embeddedSections").asLong()).isEqualTo(1);
    assertThat(meta.at("/version").asText()).isEqualTo("0.1.0");
  }

  @Test
  void shouldEqualConfiguredModels_whenMetaIsRequested() {
    JsonNode models = http.getForObject("/api/v1/meta", JsonNode.class).get("models");
    ShromikaiProperties.Models configured = properties.models();

    assertThat(models.get("reasoning").asText()).isEqualTo(configured.reasoning());
    assertThat(models.get("fast").asText()).isEqualTo(configured.fast());
    assertThat(models.get("verifierFallback").asText()).isEqualTo(configured.verifierFallback());
    assertThat(models.get("localPii").asText()).isEqualTo(configured.localPii());
    assertThat(models.get("embedding").asText()).isEqualTo(configured.embedding());
    assertThat(models.get("verifier").isNull()).isEqualTo(configured.verifierOrNull() == null);
    assertThat(models.get("judge").isNull()).isEqualTo(configured.judgeOrNull() == null);
    assertThat(models.get("reasoning").asText()).isEqualTo("claude-sonnet-5-5");
    assertThat(models.get("fast").asText()).isEqualTo("claude-haiku-5-5");
    assertThat(models.get("localPii").asText()).isEqualTo("qwen2.5:7b-instruct");
    assertThat(models.get("embedding").asText()).isEqualTo("bge-m3");
  }

  @Test
  void shouldCarryRequestId_whenMetaIsRequested() {
    ResponseEntity<String> response = http.getForEntity("/api/v1/meta", String.class);

    assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
  }
}
