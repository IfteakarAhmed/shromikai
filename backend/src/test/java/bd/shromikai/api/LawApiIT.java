package bd.shromikai.api;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.retrieval.EmbeddingBackfillService;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.ai.fake.FakeEmbeddingPort;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
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

/**
 * FR-22: the law endpoints on fixture sections (fictional pack xx, R-10): a search for a known
 * heading finds that section in the top 3, unauthenticated calls get 401, and a search makes no
 * chat model call.
 */
@IntegrationTest
@Requirement("FR-22")
class LawApiIT {

  @Autowired TestRestTemplate http;
  @Autowired JdbcClient jdbc;
  @Autowired JwtService jwt;
  @Autowired EmbeddingBackfillService backfill;
  @Autowired FakeEmbeddingPort embeddings;
  @Autowired FakeChatPort chat;

  @BeforeEach
  void fixtures() {
    clean();
    long act =
        jdbc.sql(
                """
                insert into law_act (code, title_en, title_bn, year, jurisdiction, source_url,
                                     authoritative_lang)
                values ('XR-API', 'Fictional API act', 'কাল্পনিক আইন', 2001, 'BD',
                        'https://example.test/xr-api', 'en') returning id
                """)
            .query(Long.class)
            .single();
    for (String[] s :
        new String[][] {
          {"1", "Zorblax hours", "A zorblax worker shall not work more than eight hours."},
          {"2", "Quindle leave", "Every quindle worker is entitled to ten days of leave."},
          {"3", "Plinth notice", "A plinth employer shall give thirty days notice."},
          {"10", "Wug rules", "Wug rules apply to every wug."},
        }) {
      jdbc.sql(
              """
              insert into law_section (act_id, section_no, heading_en, text_en, text_bn,
                                       version_label, is_current, source_url, content_hash,
                                       normalised_text, section_key, section_no_original)
              values (:act, :no, :heading, :text, :textBn, 'original', true,
                      'https://example.test/xr-api/' || :no, :hash, :norm,
                      'XR-API:' || :no || '@original', :no)
              """)
          .param("act", act)
          .param("no", s[0])
          .param("heading", s[1])
          .param("text", s[2])
          .param("textBn", s[0].equals("1") ? "বাংলা অনুবাদ" : null)
          .param("hash", bd.shromikai.shared.Hashing.sha256Hex(s[1] + s[2]))
          .param("norm", bd.shromikai.shared.text.BanglaNormalizer.matchingForm(s[1] + " " + s[2]))
          .update();
    }
    jdbc.sql(
            """
            insert into section_edge (from_section_id, to_section_id, kind)
            select a.id, b.id, 'REFERS_TO' from law_section a, law_section b
             where a.section_key = 'XR-API:1@original' and b.section_key = 'XR-API:2@original'
            """)
        .update();
    backfill.run();
    chat.reset();
  }

  @AfterEach
  void clean() {
    jdbc.sql("delete from law_act where code like 'XR-%'").update();
  }

  private ResponseEntity<JsonNode> get(String path, Object... uriVariables) {
    return http.exchange(
        path,
        HttpMethod.GET,
        new HttpEntity<>(
            bearer(jwt.issueUser(java.util.UUID.randomUUID(), Role.WORKER, null, false).value())),
        JsonNode.class,
        uriVariables);
  }

  @Test
  void shouldReturn401WithoutAToken() {
    for (String path :
        List.of(
            "/api/v1/laws",
            "/api/v1/laws/search?q=x",
            "/api/v1/laws/XR-API",
            "/api/v1/laws/XR-API/1")) {
      assertThat(http.getForEntity(path, JsonNode.class).getStatusCode())
          .as(path)
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Test
  void shouldAllowGuests() {
    ResponseEntity<JsonNode> response =
        http.exchange(
            "/api/v1/laws",
            HttpMethod.GET,
            new HttpEntity<>(bearer(jwt.issueGuest("d".repeat(64)).value())),
            JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldFindAKnownHeadingInTheTopThreeWithoutAnyChatCall() {
    ResponseEntity<JsonNode> response = get("/api/v1/laws/search?q={q}&k=5", "Zorblax hours");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode results = response.getBody().get("results");
    List<String> top3 = new java.util.ArrayList<>();
    for (int i = 0; i < Math.min(3, results.size()); i++) {
      top3.add(results.get(i).get("sectionKey").asText());
    }
    assertThat(top3).contains("XR-API:1@original");
    JsonNode first = results.get(0);
    assertThat(first.get("actCode").asText()).isEqualTo("XR-API");
    assertThat(first.get("headingEn").asText()).isEqualTo("Zorblax hours");
    assertThat(first.has("vectorRank")).isTrue();
    assertThat(first.get("textRank").asInt()).isEqualTo(1);
    assertThat(first.get("fusedScore").asDouble()).isPositive();
    assertThat(first.get("origin").asText()).isEqualTo("SEARCH");
    assertThat(first.get("snippet").asText().length()).isLessThanOrEqualTo(300);
    assertThat(response.getBody().get("query").asText()).isEqualTo("Zorblax hours");
    assertThat(chat.calls()).isEmpty();
  }

  @Test
  void shouldMarkGraphNeighboursAsContext() {
    JsonNode results = get("/api/v1/laws/search?q=zorblax&k=1").getBody().get("results");

    assertThat(results.get(0).get("sectionKey").asText()).isEqualTo("XR-API:1@original");
    assertThat(results)
        .anySatisfy(
            r -> {
              assertThat(r.get("sectionKey").asText()).isEqualTo("XR-API:2@original");
              assertThat(r.get("origin").asText()).isEqualTo("GRAPH");
            });
  }

  @Test
  void shouldRejectInvalidSearchParameters() {
    assertThat(get("/api/v1/laws/search").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get("/api/v1/laws/search?q={q}", "  ").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get("/api/v1/laws/search?q=x&k=21").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get("/api/v1/laws/search?q=x&k=0").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get("/api/v1/laws/search?q=x&lang=fr").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get("/api/v1/laws/search?q=x&jurisdiction=XX").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    String longQuery = "a".repeat(501);
    assertThat(get("/api/v1/laws/search?q=" + longQuery).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldListActsOfAJurisdiction() {
    JsonNode acts = get("/api/v1/laws?jurisdiction=BD").getBody();

    assertThat(acts)
        .anySatisfy(
            a -> {
              assertThat(a.get("actCode").asText()).isEqualTo("XR-API");
              assertThat(a.get("sectionCount").asInt()).isEqualTo(4);
              assertThat(a.get("year").asInt()).isEqualTo(2001);
              assertThat(a.get("authoritativeLang").asText()).isEqualTo("en");
            });
    assertThat(get("/api/v1/laws?jurisdiction=ILO").getBody())
        .noneMatch(a -> a.get("actCode").asText().equals("XR-API"));
  }

  @Test
  void shouldReturnAnActWithItsSectionsInNumericOrder() {
    JsonNode act = get("/api/v1/laws/XR-API").getBody();

    assertThat(act.get("titleEn").asText()).isEqualTo("Fictional API act");
    List<String> numbers = new java.util.ArrayList<>();
    act.get("sections").forEach(s -> numbers.add(s.get("sectionNo").asText()));
    assertThat(numbers).containsExactly("1", "2", "3", "10");
    assertThat(get("/api/v1/laws/NOPE").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldReturnASectionWithTranslationAndRelatedSections() {
    JsonNode section = get("/api/v1/laws/XR-API/1").getBody();

    assertThat(section.get("sectionKey").asText()).isEqualTo("XR-API:1@original");
    assertThat(section.get("authoritativeLang").asText()).isEqualTo("en");
    assertThat(section.get("authoritativeText").asText()).contains("zorblax worker");
    assertThat(section.at("/translation/lang").asText()).isEqualTo("bn");
    assertThat(section.at("/translation/official").asBoolean()).isFalse();
    assertThat(section.get("isCurrent").asBoolean()).isTrue();
    assertThat(section.at("/related/0/sectionKey").asText()).isEqualTo("XR-API:2@original");
    assertThat(section.at("/related/0/kind").asText()).isEqualTo("REFERS_TO");
    assertThat(get("/api/v1/laws/XR-API/2").getBody().get("translation").isNull()).isTrue();
  }

  @Test
  void shouldReturn404ForAnUnknownSectionOrVersion() {
    assertThat(get("/api/v1/laws/XR-API/999").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/api/v1/laws/XR-API/1?version=amend-1999").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/api/v1/laws/XR-API/1?version=original").getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }
}
