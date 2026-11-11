package bd.shromikai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** FR-20: graph expansion and topic-map additions. */
@IntegrationTest
@Requirement("FR-20")
class GraphExpansionIT {

  private static final String XB = "XB";

  @Autowired JdbcClient jdbc;
  @Autowired RetrievalService retrieval;

  private long seed;
  private long definition;
  private long[] refs;

  @BeforeEach
  void fixtures() {
    RetrievalFixtures.clean(jdbc);
    long lab = RetrievalFixtures.act(jdbc, RetrievalFixtures.ACT_LAB, XB);
    long def = RetrievalFixtures.act(jdbc, RetrievalFixtures.ACT_DEF, XB);
    seed =
        RetrievalFixtures.section(
            jdbc,
            lab,
            "XR-LAB",
            "10",
            "original",
            true,
            "Frobnitz duty",
            "A frobnitz employer owes the duty described in this section.");
    definition =
        RetrievalFixtures.section(
            jdbc,
            def,
            "XR-DEF",
            "2",
            "original",
            true,
            "Definitions",
            "In this act a wage means money owed, nothing more.");
    refs = new long[6];
    for (int i = 0; i < refs.length; i++) {
      refs[i] =
          RetrievalFixtures.section(
              jdbc,
              lab,
              "XR-LAB",
              String.valueOf(20 + i),
              "original",
              true,
              "Other " + i,
              "Unrelated text number " + i + ".");
      RetrievalFixtures.edge(jdbc, seed, refs[i], "REFERS_TO");
    }
    RetrievalFixtures.edge(jdbc, seed, definition, "USES_DEFINITION");
    // A non-current target must never be pulled in.
    long old =
        RetrievalFixtures.section(
            jdbc, lab, "XR-LAB", "29", "old", false, "Old", "Superseded text.");
    RetrievalFixtures.edge(jdbc, seed, old, "REFERS_TO");
  }

  @AfterEach
  void clean() {
    RetrievalFixtures.clean(jdbc);
  }

  private SearchOptions textOnly(boolean graph, String clauseType) {
    return new SearchOptions(Set.of(XB), 1, false, true, graph, false, clauseType);
  }

  @Test
  void shouldPullInDefinitionsFirstAndNeverMoreThanFourExtras() {
    Retrieval result = retrieval.search("frobnitz", textOnly(true, null));

    List<RetrievalResult> extras =
        result.results().stream().filter(r -> r.origin() == RetrievalOrigin.GRAPH).toList();
    assertThat(extras).hasSize(4);
    assertThat(extras.get(0).sectionKey()).isEqualTo("XR-DEF:2@original");
    assertThat(extras).allSatisfy(r -> assertThat(r.fusedScore()).isZero());
    assertThat(result.results().get(0).sectionKey()).isEqualTo("XR-LAB:10@original");
    assertThat(result.results().get(0).origin()).isEqualTo(RetrievalOrigin.SEARCH);
  }

  @Test
  void shouldNotDuplicateOrAddNonCurrentSections() {
    Retrieval result = retrieval.search("frobnitz", textOnly(true, null));

    List<String> keys = result.results().stream().map(RetrievalResult::sectionKey).toList();
    assertThat(keys).doesNotHaveDuplicates().doesNotContain("XR-LAB:29@old");
  }

  @Test
  void shouldAddNothingWhenGraphExpansionIsOff() {
    Retrieval result = retrieval.search("frobnitz", textOnly(false, null));

    assertThat(result.results()).hasSize(1);
  }

  @Test
  void shouldAddAtMostFourTopicMapSectionsMarkedTopicMap() {
    for (int no = 30; no <= 34; no++) {
      RetrievalFixtures.section(
          jdbc,
          jdbc.sql("select id from law_act where code = 'XR-LAB'").query(Long.class).single(),
          "XR-LAB",
          String.valueOf(no),
          "original",
          true,
          "Topic " + no,
          "Topic only " + no + ".");
    }

    Retrieval result = retrieval.search("frobnitz", textOnly(false, "OVERTIME"));

    List<String> topic =
        result.results().stream()
            .filter(r -> r.origin() == RetrievalOrigin.TOPIC_MAP)
            .map(RetrievalResult::sectionKey)
            .toList();
    assertThat(topic).hasSize(4).doesNotHaveDuplicates();
    assertThat(topic).allMatch(k -> k.matches("XR-LAB:3[0-3]@original"));
  }

  @Test
  void shouldNotMarkASectionTopicMapWhenSearchAlreadyFoundIt() {
    long lab = jdbc.sql("select id from law_act where code = 'XR-LAB'").query(Long.class).single();
    RetrievalFixtures.section(
        jdbc, lab, "XR-LAB", "40", "original", true, "Wage frobnitz", "frobnitz wage section.");

    Retrieval result = retrieval.search("frobnitz wage", textOnly(false, "WAGES"));

    assertThat(result.results())
        .filteredOn(r -> r.sectionKey().equals("XR-LAB:40@original"))
        .singleElement()
        .satisfies(r -> assertThat(r.origin()).isEqualTo(RetrievalOrigin.SEARCH));
  }
}
