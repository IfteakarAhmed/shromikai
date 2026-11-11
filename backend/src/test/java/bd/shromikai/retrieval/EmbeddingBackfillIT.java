package bd.shromikai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** FR-18: the second run embeds nothing; a changed section is embedded again. */
@IntegrationTest
@Requirement("FR-18")
class EmbeddingBackfillIT {

  @Autowired JdbcClient jdbc;
  @Autowired EmbeddingBackfillService backfill;

  private long first;

  @BeforeEach
  void fixtures() {
    RetrievalFixtures.clean(jdbc);
    long act = RetrievalFixtures.act(jdbc, RetrievalFixtures.ACT_LAB, "XB");
    first =
        RetrievalFixtures.section(
            jdbc, act, "XR-LAB", "1", "original", true, "One", "First fixture text.");
    RetrievalFixtures.section(
        jdbc, act, "XR-LAB", "2", "original", true, "Two", "Second fixture text.");
    // Embedding everything else in the shared test database is not this test's business.
    backfill.run();
  }

  @AfterEach
  void clean() {
    RetrievalFixtures.clean(jdbc);
  }

  @Test
  void shouldStoreA1024DimensionVectorAndTheHashItWasComputedFrom() {
    var row =
        jdbc.sql(
                "select vector_dims(embedding) as dims, embedded_hash, content_hash,"
                    + " embedded_model, embedding_truncated from law_section where id = :id")
            .param("id", first)
            .query()
            .singleRow();

    assertThat(row.get("dims")).isEqualTo(1024);
    assertThat(row.get("embedded_hash")).isEqualTo(row.get("content_hash"));
    assertThat(row.get("embedded_model")).isEqualTo("bge-m3");
    assertThat(row.get("embedding_truncated")).isEqualTo(false);
  }

  @Test
  void shouldEmbedNothingOnTheSecondRun() {
    BackfillResult second = backfill.run();

    assertThat(second.embedded()).isZero();
    assertThat(second.skipped()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void shouldEmbedAChangedSectionAgain() {
    String newHash = "a".repeat(64);
    jdbc.sql(
            "update law_section set content_hash = :h, text_en = 'Changed fixture text.' where id = :id")
        .param("h", newHash)
        .param("id", first)
        .update();

    BackfillResult result = backfill.run();

    assertThat(result.embedded()).isEqualTo(1);
    assertThat(
            jdbc.sql("select embedded_hash from law_section where id = :id")
                .param("id", first)
                .query(String.class)
                .single())
        .isEqualTo(newHash);
    assertThat(backfill.run().embedded()).isZero();
  }

  @Test
  void shouldEmbedASectionThatLostItsEmbedding() {
    jdbc.sql("update law_section set embedding = null where id = :id").param("id", first).update();

    assertThat(backfill.run().embedded()).isEqualTo(1);
  }

  @Test
  void shouldFlagAnInputThatIsCutAtTheContextLimit() {
    long act = jdbc.sql("select id from law_act where code = 'XR-LAB'").query(Long.class).single();
    long big =
        RetrievalFixtures.section(
            jdbc,
            act,
            "XR-LAB",
            "3",
            "original",
            true,
            "Big",
            "word ".repeat(EmbeddingBackfillService.MAX_INPUT_CHARS));

    BackfillResult result = backfill.run();

    assertThat(result.truncated()).isEqualTo(1);
    assertThat(
            jdbc.sql("select embedding_truncated from law_section where id = :id")
                .param("id", big)
                .query(Boolean.class)
                .single())
        .isTrue();
  }
}
