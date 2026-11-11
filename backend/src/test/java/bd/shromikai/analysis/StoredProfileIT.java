package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.Role;
import bd.shromikai.testsupport.IdentityFixtures;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

/** FR-56: an account's stored profile is used when a request sends none; a sent one wins. */
@Requirement("FR-56")
class StoredProfileIT extends ApiAnalysisIT {

  @Autowired PasswordEncoder encoder;

  @Test
  void theStoredReadingLevelIsUsedUnlessTheRequestSendsAProfile() {
    UUID user =
        IdentityFixtures.insertUser(
            jdbc,
            encoder,
            IdentityFixtures.uniqueEmail("stored"),
            IdentityFixtures.PASSWORD,
            Role.WORKER,
            false);
    try {
      String token = tokenFor(user);
      HttpHeaders headers = bearer(token);
      headers.setContentType(MediaType.APPLICATION_JSON);
      http.exchange(
          "/api/v1/auth/me",
          HttpMethod.PATCH,
          new HttpEntity<>(
              "{\"profile\":{\"readingLevel\":\"BASIC\",\"sector\":\"TEXTILES\"}}", headers),
          JsonNode.class);

      JsonNode stored = postJson(token).getBody();
      JsonNode sent =
          postJson(
                  token,
                  Map.of(
                      "text",
                      text(),
                      "side",
                      "WORKER",
                      "outputLanguage",
                      "en",
                      "profile",
                      Map.of("readingLevel", "STANDARD")))
              .getBody();

      assertThat(stored.path("profile").path("reading_level").asText()).isEqualTo("BASIC");
      assertThat(stored.path("memo").path("readingLevel").asText()).isEqualTo("BASIC");
      assertThat(sent.path("memo").path("readingLevel").asText()).isEqualTo("STANDARD");
    } finally {
      jdbc.sql("delete from analysis_run").update();
      jdbc.sql("delete from app_user where id = :id").param("id", user).update();
    }
  }
}
