package bd.shromikai.api;

import bd.shromikai.analysis.AnalysisResults;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-18 {@code GET /api/v1/admin/traces/{analysisId}} (ADMIN, FR-73): the content-free trace of any
 * analysis, for the admin trace viewer. The owner reads the same document at {@code
 * /analyses/{id}/trace}.
 */
@RestController
@RequestMapping("/api/v1/admin/traces")
public class AdminTraceController {

  private final AnalysisResults results;

  public AdminTraceController(AnalysisResults results) {
    this.results = results;
  }

  @GetMapping("/{analysisId}")
  public ObjectNode trace(@PathVariable UUID analysisId) {
    return results.traceAny(analysisId);
  }
}
