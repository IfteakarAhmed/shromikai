package bd.shromikai.analysis;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** {@code shromikai_jobstore_size}: in-memory analysis jobs held now (architecture §15, FR-82). */
@Component
class JobStoreGauge {

  JobStoreGauge(JobStore jobs, MeterRegistry meters) {
    Gauge.builder("shromikai_jobstore_size", jobs, JobStore::size).register(meters);
  }
}
