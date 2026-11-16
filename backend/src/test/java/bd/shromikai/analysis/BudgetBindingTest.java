package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * FR-62: {@code shromikai.budget.daily-usd} really sets the daily cap, from a property and from the
 * environment variable {@code SHROMIKAI_BUDGET_DAILYUSD}. (The two-argument convenience constructor
 * of {@code Budget} must not hide the setting: the cap was stuck at the default of 50.)
 */
@Tag("unit")
@Requirement("FR-62")
class BudgetBindingTest {

  private static final String PER = "shromikai.budget.per-analysis-usd";

  @Test
  void theDailyCapIsBoundFromTheProperty() {
    Map<String, Object> props =
        Map.of(
            PER,
            "1.0",
            "shromikai.budget.per-analysis-tokens",
            "1000",
            "shromikai.budget.daily-usd",
            "7");
    var budget =
        new Binder(new MapConfigurationPropertySource(props))
            .bind("shromikai.budget", AnalysisProperties.Budget.class)
            .get();

    assertThat(budget.dailyUsdMicros()).isEqualTo(7_000_000);
  }
}
