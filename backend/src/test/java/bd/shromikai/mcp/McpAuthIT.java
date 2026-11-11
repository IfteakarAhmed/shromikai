package bd.shromikai.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.ApiKeyService;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** FR-65, T-12: only an active key with scope mcp reaches /mcp; denials are audited. */
@Requirement("FR-65")
class McpAuthIT extends McpIT {

  private long denials() {
    return jdbc.sql(
            "select count(*) from audit_event where action = 'ACCESS_DENIED'"
                + " and actor_kind = 'API_KEY'")
        .query(Long.class)
        .single();
  }

  @Test
  void aMissingKeyIs401() {
    assertThat(initialize(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void aWrongKeyIs401AndTheDenialIsAudited() {
    long before = denials();
    String unknown = "shk_" + "A".repeat(40);

    assertThat(initialize(unknown).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(initialize("not-a-key").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    assertThat(denials()).isEqualTo(before + 2);
    assertThat(
            jdbc.sql(
                    "select detail::text from audit_event where action = 'ACCESS_DENIED'"
                        + " order by occurred_at desc limit 2")
                .query(String.class)
                .list())
        .allSatisfy(detail -> assertThat(detail).contains("invalid_key").doesNotContain(unknown));
  }

  @Test
  void aKeyWithScopeMcpIsAccepted() {
    assertThat(initialize(key("mcp", "laws")).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void aKeyWithoutScopeMcpIs403AndAudited() {
    long before = denials();

    assertThat(initialize(key("laws")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(denials()).isEqualTo(before + 1);
  }

  @Test
  void aRevokedKeyStopsWorking() {
    UUID org = organisation("Revoke org");
    ApiKeyService.Created created = keys.create(org, "temp", List.of("mcp"), null);
    assertThat(initialize(created.key()).getStatusCode()).isEqualTo(HttpStatus.OK);

    keys.revoke(org, created.view().id());

    assertThat(initialize(created.key()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void aKeyInTheQueryStringIsRefused() {
    String key = key("mcp");

    assertThat(
            http.postForEntity("/mcp?api_key=" + key, java.util.Map.of(), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anAccessTokenOfAUserDoesNotOpenMcp(
      @org.springframework.beans.factory.annotation.Autowired bd.shromikai.auth.JwtService jwt) {
    String token =
        jwt.issueUser(UUID.randomUUID(), bd.shromikai.auth.Role.ADMIN, null, false).value();

    assertThat(initialize(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
