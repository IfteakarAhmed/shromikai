package bd.shromikai.testsupport;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.HttpCookie;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Synthetic accounts and request helpers for identity tests. Every value is fictional. */
public final class IdentityFixtures {

  public static final String PASSWORD = "correct-horse-battery-1";
  public static final String REFRESH_COOKIE = "shromik_refresh";

  private IdentityFixtures() {}

  public static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
  }

  public static UUID insertUser(
      JdbcClient jdbc,
      PasswordEncoder encoder,
      String email,
      String password,
      Role role,
      boolean demo) {
    return jdbc.sql(
            """
            insert into app_user (email, password_hash, role, is_demo)
            values (:email, :hash, :role, :demo) returning id
            """)
        .param("email", email)
        .param("hash", encoder.encode(password))
        .param("role", role.name())
        .param("demo", demo)
        .query(UUID.class)
        .single();
  }

  public static HttpHeaders bearer(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return headers;
  }

  public static String userToken(JwtService jwt, Role role) {
    return jwt.issueUser(UUID.randomUUID(), role, null, false).value();
  }

  public static ResponseEntity<JsonNode> login(
      TestRestTemplate http, String email, String password) {
    return http.postForEntity(
        "/api/v1/auth/login", json(Map.of("email", email, "password", password)), JsonNode.class);
  }

  public static HttpEntity<Map<String, String>> json(Map<String, String> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  /** The value of the refresh cookie set by a response, or null if it set none. */
  public static String refreshCookie(ResponseEntity<?> response) {
    for (String header : response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)) {
      for (HttpCookie cookie : HttpCookie.parse(header)) {
        if (cookie.getName().equals(REFRESH_COOKIE)) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }

  public static HttpEntity<Void> withRefreshCookie(String value, String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, REFRESH_COOKIE + "=" + value);
    if (accessToken != null) {
      headers.setBearerAuth(accessToken);
    }
    return new HttpEntity<>(headers);
  }
}
