package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static bd.shromikai.testsupport.IdentityFixtures.userToken;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@IntegrationTest
@Requirement("FR-09")
class GuestTokenIT {

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired bd.shromikai.shared.config.ShromikaiProperties properties;

  private ResponseEntity<JsonNode> issueGuest(String deviceToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.postForEntity(
        "/api/v1/auth/guest",
        new HttpEntity<>(Map.of("deviceToken", deviceToken), headers),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> analyse(String access) {
    return http.exchange(
        "/api/v1/analyses", HttpMethod.POST, new HttpEntity<>(bearer(access)), JsonNode.class);
  }

  @Test
  void shouldIssueTwoHourWorkerToken_withoutRefreshCookieOrUser() {
    ResponseEntity<JsonNode> response = issueGuest(UUID.randomUUID().toString());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = response.getBody();
    assertThat(body.get("guest").asBoolean()).isTrue();
    assertThat(body.get("expiresIn").asInt()).isEqualTo(7200);
    assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
    assertThat(body.get("user").isNull()).isTrue();
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    AuthenticatedSubject subject = jwt.parse(body.get("accessToken").asText());
    assertThat(subject.guest()).isTrue();
    assertThat(subject.role()).isEqualTo(Role.WORKER);
  }

  @Test
  void shouldNotPutTheDeviceTokenInTheJwt_onlyItsHash() {
    String device = UUID.randomUUID().toString();
    String access = issueGuest(device).getBody().get("accessToken").asText();

    String payload = new String(java.util.Base64.getUrlDecoder().decode(access.split("\\.")[1]));

    assertThat(payload).doesNotContain(device).contains("\"guest\":true");
  }

  @Test
  void shouldRejectDeviceTokens_thatAreNotUuidV4() {
    for (String bad :
        new String[] {
          "",
          "abc",
          "123e4567-e89b-12d3-a456-426614174000",
          "not-a-uuid-at-all-not-a-uuid-at-all-xxxx"
        }) {
      assertThat(issueGuest(bad).getStatusCode()).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  void shouldReachAnalyses_butNotPlaybooks() {
    String access = issueGuest(UUID.randomUUID().toString()).getBody().get("accessToken").asText();

    assertThat(analyse(access).getStatusCode()).isEqualTo(HttpStatus.OK);
    ResponseEntity<JsonNode> playbooks =
        http.exchange(
            "/api/v1/playbooks", HttpMethod.GET, new HttpEntity<>(bearer(access)), JsonNode.class);
    assertThat(playbooks.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldReturn429QuotaExceeded_onTheFourthAnalysisOfTheDay() {
    String access = issueGuest(UUID.randomUUID().toString()).getBody().get("accessToken").asText();
    for (int i = 0; i < 3; i++) {
      assertThat(analyse(access).getStatusCode()).as("analysis %d", i + 1).isEqualTo(HttpStatus.OK);
    }

    ResponseEntity<JsonNode> fourth = analyse(access);

    assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(fourth.getBody().get("code").asText()).isEqualTo("quota-exceeded");
    assertThat(fourth.getBody().get("retryAfterSeconds").asInt()).isPositive();
  }

  @Test
  void shouldKeepTheQuota_whenTheGuestTokenIsReissuedForTheSameDevice() {
    String device = UUID.randomUUID().toString();
    for (int i = 0; i < 3; i++) {
      analyse(issueGuest(device).getBody().get("accessToken").asText());
    }

    ResponseEntity<JsonNode> again =
        analyse(issueGuest(device).getBody().get("accessToken").asText());

    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void shouldNotApplyTheGuestQuota_toSignedInUsers() {
    String token = userToken(jwt, Role.WORKER);
    for (int i = 0; i < 5; i++) {
      assertThat(analyse(token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }
}
