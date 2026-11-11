package bd.shromikai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.fake.FakeEmbeddingPort;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * FR-19: hybrid search over fixture sections with fake embeddings: both lists, fusion, current
 * version filter and jurisdiction filter.
 */
@IntegrationTest
@Requirement("FR-19")
class HybridSearchIT {

  @Autowired JdbcClient jdbc;
  @Autowired RetrievalService retrieval;
  @Autowired EmbeddingBackfillService backfill;
  @Autowired FakeEmbeddingPort embeddings;

  private static final String XB = "XB";
  private static final String XI = "XI";

  @BeforeEach
  void fixtures() {
    RetrievalFixtures.clean(jdbc);
    long lab = RetrievalFixtures.act(jdbc, RetrievalFixtures.ACT_LAB, XB);
    long ilo = RetrievalFixtures.act(jdbc, RetrievalFixtures.ACT_ILO, XI);
    RetrievalFixtures.section(
        jdbc,
        lab,
        "XR-LAB",
        "1",
        "original",
        true,
        "Zorblax hours",
        "A zorblax worker shall not work more than eight hours in a day.");
    RetrievalFixtures.section(
        jdbc,
        lab,
        "XR-LAB",
        "2",
        "original",
        true,
        "Quindle leave",
        "Every quindle worker is entitled to ten days of leave in a year.");
    RetrievalFixtures.section(
        jdbc,
        lab,
        "XR-LAB",
        "3",
        "original",
        true,
        "Plinth notice",
        "A plinth employer shall give thirty days notice before ending the service.");
    RetrievalFixtures.section(
        jdbc,
        lab,
        "XR-LAB",
        "4",
        "original",
        false,
        "Old zorblax hours",
        "Superseded: zorblax worker works ten hours in a day.");
    RetrievalFixtures.section(
        jdbc,
        ilo,
        "XR-ILO",
        "1",
        "original",
        true,
        "Zorblax convention",
        "Members shall limit the zorblax hours of work in industry.");
    backfill.run();
  }

  @AfterEach
  void clean() {
    RetrievalFixtures.clean(jdbc);
  }

  private void pinQueryToSection(String query, String heading, String text) {
    embeddings.pin(
        query, embeddings.embed(List.of(RetrievalFixtures.embeddingInput(heading, text))).get(0));
  }

  private static SearchOptions hybrid(Set<String> jurisdictions) {
    return new SearchOptions(jurisdictions, 8, true, true, false, false, null);
  }

  @Test
  void shouldRankASectionFoundByBothListsFirstAndRecordBothRanks() {
    pinQueryToSection(
        "zorblax working time",
        "Zorblax hours",
        "A zorblax worker shall not work more than eight hours in a day.");

    Retrieval result = retrieval.search("zorblax working time", hybrid(Set.of(XB)));

    RetrievalResult first = result.results().get(0);
    assertThat(first.sectionKey()).isEqualTo("XR-LAB:1@original");
    assertThat(first.vectorRank()).isEqualTo(1);
    assertThat(first.textRank()).isEqualTo(1);
    assertThat(first.fusedScore()).isCloseTo(2.0 / 61, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(first.origin()).isEqualTo(RetrievalOrigin.SEARCH);
    assertThat(result.keywordOnly()).isFalse();
  }

  @Test
  void shouldFindASectionByVectorAloneWhenNoWordMatches() {
    pinQueryToSection(
        "qqq www",
        "Plinth notice",
        "A plinth employer shall give thirty days notice before ending the service.");

    Retrieval result = retrieval.search("qqq www", hybrid(Set.of(XB)));

    RetrievalResult first = result.results().get(0);
    assertThat(first.sectionKey()).isEqualTo("XR-LAB:3@original");
    assertThat(first.vectorRank()).isEqualTo(1);
    assertThat(first.textRank()).isNull();
  }

  @Test
  void shouldFindASectionByTextAloneWhenVectorsAreOff() {
    Retrieval result =
        retrieval.search(
            "quindle", new SearchOptions(Set.of(XB), 8, false, true, false, false, null));

    assertThat(result.results())
        .extracting(RetrievalResult::sectionKey)
        .containsExactly("XR-LAB:2@original");
    assertThat(result.results().get(0).vectorRank()).isNull();
    assertThat(result.results().get(0).textRank()).isEqualTo(1);
  }

  @Test
  void shouldNeverReturnANonCurrentVersion() {
    Retrieval result = retrieval.search("zorblax hours", hybrid(Set.of(XB)));

    assertThat(result.results())
        .extracting(RetrievalResult::sectionKey)
        .doesNotContain("XR-LAB:4@original")
        .contains("XR-LAB:1@original");
  }

  @Test
  void shouldFilterByJurisdiction() {
    assertThat(retrieval.search("zorblax hours", hybrid(Set.of(XB))).results())
        .extracting(RetrievalResult::actCode)
        .containsOnly("XR-LAB");
    assertThat(retrieval.search("zorblax hours", hybrid(Set.of(XI))).results())
        .extracting(RetrievalResult::actCode)
        .containsOnly("XR-ILO");
    assertThat(retrieval.search("zorblax hours", hybrid(Set.of(XB, XI))).results())
        .extracting(RetrievalResult::actCode)
        .contains("XR-LAB", "XR-ILO");
  }

  @Test
  void shouldRankDeterministicallyAcrossRuns() {
    List<String> first = keys(retrieval.search("zorblax hours leave notice", hybrid(Set.of(XB))));
    List<String> second = keys(retrieval.search("zorblax hours leave notice", hybrid(Set.of(XB))));

    assertThat(second).isEqualTo(first);
  }

  private static List<String> keys(Retrieval retrieval) {
    return retrieval.results().stream().map(RetrievalResult::sectionKey).toList();
  }

  @Test
  void shouldMatchOnlySectionsInTheQueryLanguageWhenTheLanguageFilterIsOn() {
    long bn = RetrievalFixtures.act(jdbc, "XR-BN", XB, "bn");
    RetrievalFixtures.section(
        jdbc, bn, "XR-BN", "1", "original", true, "Zorblax", "zorblax বাংলা পাঠ।");
    SearchOptions textOnly =
        new SearchOptions(Set.of(XB), 8, false, true, false, false, null)
            .withTextTuning(new TextTuning(1.0, true));

    assertThat(keys(retrieval.search("zorblax", textOnly)))
        .contains("XR-LAB:1@original")
        .doesNotContain("XR-BN:1@original");
    assertThat(keys(retrieval.search("zorblax বাংলা", textOnly)))
        .containsExactly("XR-BN:1@original");
    assertThat(keys(retrieval.search("zorblax", textOnly.withTextTuning(TextTuning.OFF))))
        .contains("XR-LAB:1@original", "XR-BN:1@original");
  }
}
