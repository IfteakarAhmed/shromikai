package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** FR-62: the daily spend is stored, shared across instances, and the cap stops new analyses. */
@IntegrationTest
@Requirement("FR-62")
class BudgetIT {

  @Autowired DailyBudget budget;
  @Autowired JdbcClient jdbc;

  @AfterEach
  void clean() {
    jdbc.sql("delete from daily_spend").update();
  }

  @Test
  void spendingTheWholeCapRefusesTheNextAnalysisWithBudgetExceeded() {
    budget.admit();

    budget.record(budget.remainingMicros());

    assertThatThrownBy(budget::admit)
        .isInstanceOfSatisfying(
            ProblemException.class,
            p -> assertThat(p.type()).isEqualTo(ProblemType.BUDGET_EXCEEDED));
  }

  @Test
  void recordedSpendAccumulatesInOneRowPerDay() {
    budget.record(1_000);
    budget.record(2_500);

    assertThat(jdbc.sql("select cost_usd_micros from daily_spend").query(Long.class).single())
        .isEqualTo(3_500);
  }
}
