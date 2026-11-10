package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.login;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Without the demo profile there is no demo endpoint and no seeded account (FR-08). */
@IntegrationTest
@Requirement("FR-08")
class DemoLoginDisabledIT {

  @Autowired TestRestTemplate http;
  @Autowired ApplicationContext context;

  @Test
  void shouldReturn404_forEveryDemoRole() {
    for (String role : new String[] {"worker", "hr", "admin"}) {
      ResponseEntity<JsonNode> response =
          http.postForEntity("/api/v1/auth/demo/" + role, null, JsonNode.class);
      assertThat(response.getStatusCode()).as(role).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().get("code").asText()).isEqualTo("not-found");
    }
  }

  @Test
  void shouldNotSeedDemoAccounts_andNotCreateTheDemoBeans() {
    // The database is shared with DemoLoginIT, so the rows cannot prove it; the beans can.
    assertThat(context.containsBean("demoAccountSeeder")).isFalse();
    assertThat(context.getBeanNamesForType(bd.shromikai.api.DemoAuthController.class)).isEmpty();
    assertThat(login(http, "worker@demo.shromikai.app", "anything-at-all-123").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
