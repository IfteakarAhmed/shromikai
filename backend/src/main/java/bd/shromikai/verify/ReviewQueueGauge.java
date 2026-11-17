package bd.shromikai.verify;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code shromikai_review_queue_open}: review items still open (architecture §15, FR-82), read from
 * the database when the gauge is scraped; NaN when the database cannot be read.
 */
@Component
class ReviewQueueGauge {

  ReviewQueueGauge(JdbcClient jdbc, MeterRegistry meters) {
    Gauge.builder(
            "shromikai_review_queue_open",
            () -> {
              try {
                return jdbc.sql("select count(*) from review_item where status = 'OPEN'")
                    .query(Long.class)
                    .single();
              } catch (RuntimeException e) {
                return Double.NaN;
              }
            })
        .register(meters);
  }
}
