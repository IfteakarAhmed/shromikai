package bd.shromikai.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.Requirement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("FR-65")
class ApiKeyGeneratorTest {

  @Test
  void aKeyIsShkUnderscoreAndFortyBase62Characters() {
    for (int i = 0; i < 200; i++) {
      assertThat(ApiKeyGenerator.generate()).matches("shk_[0-9A-Za-z]{40}");
    }
  }

  @Test
  void keysDoNotRepeat() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 2_000; i++) {
      assertThat(seen.add(ApiKeyGenerator.generate())).isTrue();
    }
  }

  @Test
  void theStoredHashIsTheSha256OfTheWholeKeyAndThePrefixIsItsFirstEightCharacters() {
    String key = ApiKeyGenerator.generate();

    assertThat(ApiKeyGenerator.hash(key)).isEqualTo(Hashing.sha256Hex(key)).hasSize(64);
    assertThat(ApiKeyGenerator.prefix(key)).isEqualTo(key.substring(0, 8)).startsWith("shk_");
  }

  @Test
  void onlyWellFormedKeysLookLikeKeys() {
    assertThat(ApiKeyGenerator.looksLikeKey(ApiKeyGenerator.generate())).isTrue();
    assertThat(ApiKeyGenerator.looksLikeKey("shk_short")).isFalse();
    assertThat(ApiKeyGenerator.looksLikeKey("eyJhbGciOiJIUzI1NiJ9.x.y")).isFalse();
    assertThat(ApiKeyGenerator.looksLikeKey("shk_" + "!".repeat(40))).isFalse();
  }
}
