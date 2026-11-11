package bd.shromikai.api;

import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.verify.ReviewDesk;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-23 to E-25 {@code /api/v1/review-items} (ADMIN, audited; FR-60). The review queue holds
 * findings that no verified citation backs; items contain no contract text (R-02).
 */
@RestController
@Validated
@RequestMapping("/api/v1/review-items")
public class ReviewItemController {

  private final ReviewDesk desk;

  public ReviewItemController(ReviewDesk desk) {
    this.desk = desk;
  }

  @GetMapping
  public ObjectNode list(
      @RequestParam(required = false) @Nullable String status,
      @RequestParam(required = false) @Nullable String kind,
      @RequestParam(required = false) @Nullable String clauseType,
      @RequestParam(required = false) @Nullable String severity,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return desk.page(new ReviewDesk.Filter(status, kind, clauseType, severity), page, size);
  }

  @GetMapping("/{id}")
  public ObjectNode get(@PathVariable UUID id) {
    return desk.get(id).orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
  }

  @PatchMapping("/{id}")
  public ObjectNode patch(
      @AuthenticationPrincipal AuthenticatedSubject admin,
      @PathVariable UUID id,
      @RequestBody ReviewDesk.Patch patch) {
    if (admin.userId() == null) {
      throw new ProblemException(ProblemType.FORBIDDEN);
    }
    return desk.patch(id, admin.userId(), patch);
  }
}
