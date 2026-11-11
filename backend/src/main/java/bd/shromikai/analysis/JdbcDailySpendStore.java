package bd.shromikai.analysis;

import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@link DailySpendStore} over the {@code daily_spend} table (V7). Safe across instances. */
@Repository
class JdbcDailySpendStore implements DailySpendStore {

  private final JdbcClient jdbc;

  JdbcDailySpendStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public long add(LocalDate day, long micros) {
    return jdbc.sql(
            """
            insert into daily_spend (day, cost_usd_micros) values (:day, :micros)
            on conflict (day) do update
              set cost_usd_micros = daily_spend.cost_usd_micros + excluded.cost_usd_micros
            returning cost_usd_micros
            """)
        .param("day", day)
        .param("micros", micros)
        .query(Long.class)
        .single();
  }

  @Override
  public long spentOn(LocalDate day) {
    return jdbc.sql("select cost_usd_micros from daily_spend where day = :day")
        .param("day", day)
        .query(Long.class)
        .optional()
        .orElse(0L);
  }
}
