package bd.shromikai.law.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.law.internal.DefinitionExtractor.Definition;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-16: terms of the definitions section and their use elsewhere. */
@Tag("unit")
@Requirement("FR-16")
class DefinitionExtractorTest {

  @Test
  void shouldExtractDefinedTerms_fromLabourActDefinitionsSection() {
    ParsedAct act = LawFixtures.parse("labour-act-2006");
    String definitions =
        act.sections().stream()
            .filter(s -> s.sectionNo().equals("2"))
            .findFirst()
            .orElseThrow()
            .text();

    List<Definition> terms = DefinitionExtractor.definitions(definitions);

    assertThat(terms).hasSizeGreaterThan(50);
    assertThat(terms).extracting(Definition::term).contains("অবসর", "শ্রমিক");
    assertThat(terms).extracting(Definition::matching).doesNotHaveDuplicates();
  }

  @Test
  void shouldFindUsesOfDefinedTerms_withBanglaInflection() {
    List<Definition> terms =
        DefinitionExtractor.definitions(
            "(১) \"শ্রমিক\" অর্থ কোন ব্যক্তি\n(২) \"মালিক\" অর্থ নিয়োগকর্তা");

    assertThat(DefinitionExtractor.usedIn(terms, "শ্রমিকের চাকুরী"))
        .extracting(Definition::term)
        .containsExactly("শ্রমিক");
    assertThat(DefinitionExtractor.usedIn(terms, "কোন অধিকার নাই")).isEmpty();
  }

  @Test
  void shouldExtractEnglishDefinitions() {
    List<Definition> terms =
        DefinitionExtractor.definitions(
            "the person making the proposal is called the \"promisor\", and the person accepting is called the \"promisee\"\n"
                + "\"Employer\" means the person who employs");

    assertThat(terms)
        .extracting(Definition::term)
        .containsExactly("promisor", "promisee", "Employer");
    assertThat(DefinitionExtractor.usedIn(terms, "the Promisor shall pay")).hasSize(1);
  }

  @Test
  void shouldIgnoreVeryShortTermsAndMatchOnlyAtWordStart() {
    List<Definition> terms =
        DefinitionExtractor.definitions("(১) \"আয়\" অর্থ কিছু\n(২) \"মজুরী\" অর্থ বেতন");

    assertThat(terms).extracting(Definition::term).containsExactly("মজুরী");
    assertThat(DefinitionExtractor.usedIn(terms, "অতিরিক্তমজুরী")).isEmpty();
  }
}
