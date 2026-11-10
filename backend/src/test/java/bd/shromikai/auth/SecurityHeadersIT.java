package bd.shromikai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@IntegrationTest
@Requirement("FR-11")
class SecurityHeadersIT {

  @Autowired TestRestTemplate http;

  @Test
  void shouldSendTheSecurityHeaders_onApiResponses() {
    HttpHeaders headers = http.getForEntity("/api/v1/meta", String.class).getHeaders();

    String csp = headers.getFirst("Content-Security-Policy");
    assertThat(csp).contains("default-src 'self'").contains("frame-ancestors 'none'");
    assertThat(csp).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
    assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
    assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
  }

  @Test
  void shouldSendTheSecurityHeaders_onProblemResponses() {
    HttpHeaders headers = http.getForEntity("/api/v1/auth/me", String.class).getHeaders();

    assertThat(headers.getFirst("Content-Security-Policy")).contains("frame-ancestors 'none'");
    assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  void shouldNotSendHsts_outsideProd() {
    assertThat(http.getForEntity("/api/v1/meta", String.class).getHeaders())
        .doesNotContainKey("Strict-Transport-Security");
  }

  @Test
  void shouldAllowStylesInSwaggerUi_butNothingInlineForScripts() {
    HttpHeaders headers = http.getForEntity("/swagger-ui/index.html", String.class).getHeaders();

    String csp = headers.getFirst("Content-Security-Policy");
    assertThat(csp).contains("frame-ancestors 'none'").contains("style-src 'self' 'unsafe-inline'");
    assertThat(csp).doesNotContain("script-src 'unsafe-inline'");
  }

  @Test
  void shouldAnswerAConfiguredOrigin_withCorsHeaders() {
    ResponseEntity<String> response = withOrigin("http://localhost:5173");

    assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin"))
        .isEqualTo("http://localhost:5173");
    assertThat(response.getHeaders().getFirst("Access-Control-Allow-Credentials"))
        .isEqualTo("true");
  }

  @Test
  void shouldSendNoCorsHeaders_toAnUnlistedOrigin() {
    ResponseEntity<String> response = withOrigin("https://evil.example");

    assertThat(response.getHeaders()).doesNotContainKey("Access-Control-Allow-Origin");
    assertThat(response.getHeaders()).doesNotContainKey("Access-Control-Allow-Credentials");
  }

  @Test
  void shouldAnswerAPreflight_onlyForAConfiguredOrigin() {
    HttpHeaders allowed = preflight("http://localhost:5173");
    HttpHeaders denied = preflight("https://evil.example");

    assertThat(allowed.getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    assertThat(allowed.getFirst("Access-Control-Allow-Methods")).contains("POST");
    assertThat(denied).doesNotContainKey("Access-Control-Allow-Origin");
  }

  private ResponseEntity<String> withOrigin(String origin) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.ORIGIN, origin);
    return http.exchange("/api/v1/meta", HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private HttpHeaders preflight(String origin) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.ORIGIN, origin);
    headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
    headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
    return http.exchange(
            "/api/v1/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class)
        .getHeaders();
  }
}
