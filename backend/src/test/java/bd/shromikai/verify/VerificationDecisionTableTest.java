package bd.shromikai.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.DecisionTable.Decision;
import bd.shromikai.verify.DecisionTable.SupportInput;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * FR-45, architecture §7.4: every combination of existence (a), quote (b) and support (c) gives the
 * table's status, and every combination outside the table is refused. There is no WEAK status.
 */
@Tag("unit")
@Requirement("FR-45")
class VerificationDecisionTableTest {

  private static final double MIN = 0.70;

  private static final List<CheckOutcome> A = List.of(CheckOutcome.PASS, CheckOutcome.FAIL);
  private static final List<CheckOutcome> B =
      List.of(CheckOutcome.PASS, CheckOutcome.FAIL, CheckOutcome.SKIPPED);

  private static final List<SupportInput> C =
      List.of(
          new SupportInput.Judged(SupportLabel.SUPPORTS, 0.95),
          new SupportInput.Judged(SupportLabel.SUPPORTS, 0.70),
          new SupportInput.Judged(SupportLabel.SUPPORTS, 0.69),
          new SupportInput.Judged(SupportLabel.PARTIAL, 0.99),
          new SupportInput.Judged(SupportLabel.CONTRADICTS, 0.99),
          new SupportInput.Judged(SupportLabel.UNRELATED, 0.99),
          new SupportInput.Provenance(false),
          new SupportInput.Provenance(true),
          new SupportInput.Unavailable(),
          new SupportInput.Skipped());

  /** The expected status of a combination of the table; null when the table has no such row. */
  private static VerificationStatus expected(CheckOutcome a, CheckOutcome b, SupportInput c) {
    if (a == CheckOutcome.FAIL) {
      return b == CheckOutcome.SKIPPED && c instanceof SupportInput.Skipped
          ? VerificationStatus.REJECTED
          : null;
    }
    if (b == CheckOutcome.FAIL) {
      return c instanceof SupportInput.Skipped ? VerificationStatus.REJECTED : null;
    }
    if (b != CheckOutcome.PASS) {
      return null;
    }
    return switch (c) {
      case SupportInput.Skipped s -> null;
      case SupportInput.Provenance p -> VerificationStatus.VERIFIED;
      case SupportInput.Unavailable u -> VerificationStatus.UNCERTAIN;
      case SupportInput.Judged j ->
          switch (j.label()) {
            case SUPPORTS ->
                j.confidence() >= MIN ? VerificationStatus.VERIFIED : VerificationStatus.UNCERTAIN;
            case PARTIAL -> VerificationStatus.UNCERTAIN;
            case CONTRADICTS, UNRELATED -> VerificationStatus.REJECTED;
          };
    };
  }

  static Stream<Arguments> everyCombination() {
    List<Arguments> all = new ArrayList<>();
    for (CheckOutcome a : A) {
      for (CheckOutcome b : B) {
        for (SupportInput c : C) {
          all.add(Arguments.of(a, b, c));
        }
      }
    }
    return all.stream();
  }

  @ParameterizedTest(name = "a={0} b={1} c={2}")
  @MethodSource("everyCombination")
  void shouldGiveTheTableStatus_orRefuseACombinationOutsideTheTable(
      CheckOutcome a, CheckOutcome b, SupportInput c) {
    VerificationStatus expected = expected(a, b, c);

    if (expected == null) {
      assertThatThrownBy(() -> DecisionTable.decide(a, b, c, MIN))
          .isInstanceOf(IllegalArgumentException.class);
    } else {
      assertThat(DecisionTable.decide(a, b, c, MIN).status()).isEqualTo(expected);
    }
  }

  @Test
  void shouldCoverEverySixtyCombinations() {
    assertThat(everyCombination().count()).isEqualTo(60);
    long valid =
        everyCombination()
            .map(Arguments::get)
            .filter(
                args ->
                    expected((CheckOutcome) args[0], (CheckOutcome) args[1], (SupportInput) args[2])
                        != null)
            .count();
    // 1 (a FAIL) + 1 (b FAIL) + 9 (a, b PASS with a real support input)
    assertThat(valid).isEqualTo(11);
  }

  @Test
  void shouldNameTheSupportReasonOfEachNonVerifiedOutcome() {
    assertThat(support(new SupportInput.Judged(SupportLabel.SUPPORTS, 0.5)))
        .containsExactly(ReasonCode.SUPPORT_LOW_CONFIDENCE);
    assertThat(support(new SupportInput.Judged(SupportLabel.PARTIAL, 0.9)))
        .containsExactly(ReasonCode.SUPPORT_PARTIAL);
    assertThat(support(new SupportInput.Judged(SupportLabel.CONTRADICTS, 0.9)))
        .containsExactly(ReasonCode.SUPPORT_CONTRADICTS);
    assertThat(support(new SupportInput.Judged(SupportLabel.UNRELATED, 0.9)))
        .containsExactly(ReasonCode.SUPPORT_UNRELATED);
    assertThat(support(new SupportInput.Unavailable()))
        .containsExactly(ReasonCode.VERIFIER_UNAVAILABLE);
    assertThat(support(new SupportInput.Judged(SupportLabel.SUPPORTS, 0.9))).isEmpty();
  }

  @Test
  void shouldHaveNoWeakStatus() {
    assertThat(VerificationStatus.values())
        .extracting(Enum::name)
        .containsExactly("VERIFIED", "UNCERTAIN", "REJECTED");
  }

  private static List<ReasonCode> support(SupportInput input) {
    Decision decision = DecisionTable.decide(CheckOutcome.PASS, CheckOutcome.PASS, input, MIN);
    return decision.supportReasons();
  }
}
