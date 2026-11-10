package bd.shromikai.shared.ai.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("NFR-19")
class FakeEmbeddingPortTest {

  private final FakeEmbeddingPort fake = new FakeEmbeddingPort("bge-m3");

  @Test
  void shouldReturnStableUnitVectors_whenTextRepeats() {
    List<float[]> first = fake.embed(List.of("ধারা ১০০", "section 100"));
    List<float[]> again = new FakeEmbeddingPort("bge-m3").embed(List.of("ধারা ১০০"));

    assertThat(first).hasSize(2);
    assertThat(first.get(0)).hasSize(1024).containsExactly(again.get(0));
    assertThat(norm(first.get(0))).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    assertThat(first.get(0)).isNotEqualTo(first.get(1));
    assertThat(fake.dimensions()).isEqualTo(1024);
    assertThat(fake.modelId()).isEqualTo("bge-m3");
  }

  @Test
  void shouldReturnPinnedVector_whenOneIsSet() {
    float[] vector = new float[1024];
    vector[3] = 2f;
    fake.pin("probe", vector);

    float[] result = fake.embed(List.of("probe")).get(0);

    assertThat(result[3]).isEqualTo(1f);
    assertThatThrownBy(() -> fake.pin("bad", new float[3]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static double norm(float[] vector) {
    double sum = 0;
    for (float value : vector) {
      sum += value * value;
    }
    return Math.sqrt(sum);
  }
}
