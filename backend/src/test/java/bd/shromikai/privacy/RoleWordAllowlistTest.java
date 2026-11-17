package bd.shromikai.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ADR-0025: the role-word list is data, matches whole values only and never drops real PII. */
@Tag("unit")
@Requirement("FR-29")
@Requirement("FR-30")
class RoleWordAllowlistTest {

  private final RoleWordAllowlist list = RoleWordAllowlist.bundled();

  private static RoleWordAllowlist of(String yaml) {
    return new RoleWordAllowlist(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "মালিক",
        "শ্রমিক",
        "কর্মচারী",
        "স্বাক্ষর",
        "  মালিক  ",
        "\"মালিক\"",
        "স্বাক্ষর:",
        "(কর্মচারী)",
        "দ্বিতীয়   পক্ষ",
        "Employer",
        "employee",
        "WORKER"
      })
  void shouldRecogniseAGenericRoleWordAsAWholeValue(String value) {
    assertThat(list.isRoleWord(value)).as(value).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "রহিম নমুনা সরকার",
        "মালিক রহিম নমুনা সরকার",
        "আব্দুল মালিক",
        "মালিকের",
        "কর্মচারীকে",
        "উদাহরণ গার্মেন্টস লিমিটেড",
        "১২৩৪৫৬৭৮৯০১২৩",
        "namuna@example.org",
        "Employer Rahim Sarker",
        "",
        "   ",
        "।"
      })
  void shouldNotDropANameAnInflectedFormOrAnythingThatIsMoreThanTheWord(String value) {
    assertThat(list.isRoleWord(value)).as(value).isFalse();
  }

  @Test
  void shouldDropRoleWordsFromEveryKindAndKeepTheRest() {
    var entities =
        new LocalEntities(
            List.of("মালিক", "রহিম নমুনা সরকার", "স্বাক্ষর"),
            List.of("কোম্পানি", "উদাহরণ গার্মেন্টস লিমিটেড"),
            List.of("কারখানা", "প্লট ১২, কাল্পনিক শিল্প এলাকা"),
            List.of("EMP-4471"));

    LocalEntities kept = list.withoutRoleWords(entities);

    assertThat(kept.persons()).containsExactly("রহিম নমুনা সরকার");
    assertThat(kept.organisations()).containsExactly("উদাহরণ গার্মেন্টস লিমিটেড");
    assertThat(kept.addresses()).containsExactly("প্লট ১২, কাল্পনিক শিল্প এলাকা");
    assertThat(kept.employeeIds()).containsExactly("EMP-4471");
  }

  @Test
  void shouldKeepEveryInventedIdentifierOfTheFiveSyntheticContracts() {
    for (String id : SyntheticContracts.IDS) {
      for (SyntheticContracts.Entity e : SyntheticContracts.entities(id)) {
        assertThat(list.isRoleWord(e.value())).as("%s %s", id, e.type()).isFalse();
      }
    }
  }

  @Test
  void shouldKeepNoListedWordThatIsAPartOfAnInventedName() {
    for (String id : SyntheticContracts.IDS) {
      for (SyntheticContracts.Entity e : SyntheticContracts.entities(id)) {
        for (String part : e.value().split("\\s+")) {
          assertThat(list.isRoleWord(part)).as("%s part of %s", part, id).isFalse();
        }
      }
    }
  }

  @Test
  void shouldNotBeEmptyAndShouldStayDraftUntilAHumanHasReviewedIt() throws Exception {
    String file = Files.readString(Path.of("..", "data", "privacy", "role-words.yaml"));

    assertThat(list.size()).isGreaterThanOrEqualTo(20);
    assertThat(file).contains("status: draft").contains("reviewed_by: null");
  }

  @Test
  void shouldMatchNothingWhenNoneIsConfigured() {
    assertThat(RoleWordAllowlist.none().isRoleWord("মালিক")).isFalse();
  }

  @Test
  void shouldRejectAFileWithoutWords() {
    assertThatThrownBy(() -> of("authored_by: x\n")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> of("words:\n  bn: []\n")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectAnEntryThatLooksLikeAnIdentifier() {
    for (String bad : List.of("১২৩৪", "a@b.org", "01712345678", "\"\"", "x/y")) {
      assertThatThrownBy(() -> of("words:\n  bn:\n    - '" + bad + "'\n"))
          .as(bad)
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void shouldFailFastWhenTheFileIsMissing() {
    assertThatThrownBy(() -> new RoleWordAllowlist(Path.of("no-such-role-words.yaml")))
        .isInstanceOf(IllegalStateException.class);
  }
}
