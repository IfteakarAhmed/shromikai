package bd.shromikai.api;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.trace.UsageReportService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-17 {@code GET /api/v1/admin/usage} (ADMIN, FR-73): usage and cost per day over at most 92 days
 * plus the KPI-12 to KPI-14 block. The default window is the last seven days in Asia/Dhaka.
 */
@RestController
@RequestMapping("/api/v1/admin/usage")
public class AdminUsageController {

  private final UsageReportService reports;
  private final Clock clock;

  public AdminUsageController(UsageReportService reports, Clock clock) {
    this.reports = reports;
    this.clock = clock;
  }

  @GetMapping
  public ObjectNode usage(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to,
      @RequestParam(required = false) @Nullable UUID orgId) {
    LocalDate last = to != null ? to : LocalDate.now(clock.withZone(UsageReportService.ZONE));
    LocalDate first = from != null ? from : last.minusDays(UsageReportService.KPI_DAYS - 1L);
    try {
      return reports.report(orgId, first, last);
    } catch (IllegalArgumentException e) {
      throw new ProblemException(ProblemType.VALIDATION_FAILED, e.getMessage());
    }
  }
}
