package bd.shromikai.retrieval.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.testsupport.Requirement;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-20: the topic map loads, validates key shape and caps per clause type. */
@Tag("unit")
@Requirement("FR-20")
class TopicMapTest {

  private static TopicMap map(String yaml, int max) {
    return new TopicMap(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), max);
  }

  @Test
  void shouldCapSectionsPerClauseType() {
    TopicMap topics =
        map(
            """
            topics:
              WAGES:
                sections: ["XR-A:1@original", "XR-A:2@original", "XR-A:3@original"]
            """,
            2);

    assertThat(topics.sectionsFor("WAGES"))
        .extracting(Object::toString)
        .containsExactly("XR-A:1@original", "XR-A:2@original");
    assertThat(topics.sectionsFor("LEAVE")).isEmpty();
  }

  @Test
  void shouldRejectAMalformedSectionKey() {
    assertThatThrownBy(() -> map("topics:\n  WAGES:\n    sections: [\"not a key\"]\n", 4))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("malformed");
  }

  @Test
  void shouldRejectAFileWithoutTopics() {
    assertThatThrownBy(() -> map("other: 1\n", 4)).isInstanceOf(IllegalStateException.class);
  }
}
