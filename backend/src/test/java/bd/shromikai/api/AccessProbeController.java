package bd.shromikai.api;

import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.auth.GuestQuotaService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stand-ins for endpoints that later phases add (analyses P6, playbooks and admin P7, review items
 * P7), so the access matrix of api.md §2 can be tested now. A real controller replaces its probe
 * path when its phase lands.
 */
@Hidden
@Profile("test")
@RestController
class AccessProbeController {

  private final GuestQuotaService quotas;

  AccessProbeController(GuestQuotaService quotas) {
    this.quotas = quotas;
  }

  @RequestMapping(
      path = {
        "/api/v1/admin/probe",
        "/api/v1/dev/probe",
        "/api/v1/playbooks",
        "/api/v1/review-items",
        "/api/v1/compare/probe"
      },
      method = {RequestMethod.GET, RequestMethod.POST})
  Map<String, Boolean> ok() {
    return Map.of("ok", true);
  }

  /** Stands in for POST /api/v1/analyses: consumes the guest quota exactly as the real one will. */
  @RequestMapping(path = "/api/v1/analyses", method = RequestMethod.POST)
  Map<String, Boolean> analyses(
      @AuthenticationPrincipal AuthenticatedSubject subject, HttpServletRequest request) {
    try (GuestQuotaService.Reservation ignored = quotas.reserve(subject, request.getRemoteAddr())) {
      return Map.of("ok", true);
    }
  }
}
