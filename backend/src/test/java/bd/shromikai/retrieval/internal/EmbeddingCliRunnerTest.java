package bd.shromikai.retrieval.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bd.shromikai.retrieval.BackfillResult;
import bd.shromikai.retrieval.EmbeddingBackfillService;
import bd.shromikai.shared.ai.LocalModelUnavailableException;
import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

/** FR-18: the backfill job exits 0 on success and 1 when the embedding model is unavailable. */
@Tag("unit")
@Requirement("FR-18")
class EmbeddingCliRunnerTest {

  private final EmbeddingBackfillService service = mock(EmbeddingBackfillService.class);
  private final EmbeddingCliRunner runner =
      new EmbeddingCliRunner(service, mock(ApplicationContext.class));

  @Test
  void shouldExitZeroWhenTheBackfillSucceeds() {
    when(service.run()).thenReturn(new BackfillResult(3, 5, 0));

    assertThat(runner.execute()).isZero();
  }

  @Test
  void shouldExitOneWhenTheEmbeddingModelIsUnavailable() {
    when(service.run())
        .thenThrow(new LocalModelUnavailableException("ollama-embedding-unavailable"));

    assertThat(runner.execute()).isEqualTo(1);
  }
}
