package bd.shromikai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;

/** HSTS is on when {@code shromikai.auth.hsts} is true, as the prod profile sets it (FR-11). */
@IntegrationTest
@Requirement("FR-11")
@TestPropertySource(properties = "shromikai.auth.hsts=true")
class SecurityHeadersProdIT {

  @Autowired TestRestTemplate http;

  @Test
  void shouldSendHsts_whenEnabled() {
    String hsts =
        http.getForEntity("/api/v1/meta", String.class)
            .getHeaders()
            .getFirst("Strict-Transport-Security");

    assertThat(hsts).contains("max-age=31536000").contains("includeSubDomains");
  }
}
