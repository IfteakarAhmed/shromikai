package bd.shromikai.shared.json;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-55: RFC 8785 canonical form, so a client's re-serialised payload signs identically. */
@Tag("unit")
@Requirement("FR-55")
class CanonicalJsonTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static String canon(String json) throws Exception {
    return CanonicalJson.of(MAPPER.readTree(json));
  }

  @Test
  void sortsObjectKeysAtEveryLevelAndDropsWhitespace() throws Exception {
    assertThat(canon("{ \"b\": 1, \"a\": { \"d\": [1, 2], \"c\": null } }"))
        .isEqualTo("{\"a\":{\"c\":null,\"d\":[1,2]},\"b\":1}");
  }

  @Test
  void keepsArrayOrder() throws Exception {
    assertThat(canon("[3,1,2]")).isEqualTo("[3,1,2]");
  }

  @Test
  void numbersHaveOneForm() throws Exception {
    assertThat(canon("[1.0, 1, 1e2, 0.5, -0, 10.50]")).isEqualTo("[1,1,100,0.5,0,10.5]");
  }

  @Test
  void escapesControlCharactersButKeepsBanglaAsIs() throws Exception {
    assertThat(canon("{\"t\":\"ক\\n\\\"\"}")).isEqualTo("{\"t\":\"ক\\n\\\"\"}");
  }

  @Test
  void differentKeyOrderGivesTheSameBytes() throws Exception {
    assertThat(canon("{\"x\":1,\"y\":2}")).isEqualTo(canon("{\"y\":2,\"x\":1}"));
  }
}
