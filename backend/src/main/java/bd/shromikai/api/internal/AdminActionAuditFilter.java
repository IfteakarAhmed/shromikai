package bd.shromikai.api.internal;

import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.auth.Role;
import bd.shromikai.trace.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes an {@code ADMIN_ACTION} audit row for every successful write under {@code /api/v1/admin/}
 * by an admin (FR-10), so each admin endpoint added later is audited without remembering to. It
 * runs after authorisation, so a refused request never reaches it.
 */
final class AdminActionAuditFilter extends OncePerRequestFilter {

  private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final AuditService audit;

  AdminActionAuditFilter(AuditService audit) {
    this.audit = audit;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/admin/")
        || !WRITES.contains(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(request, response);
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (response.getStatus() < 400
        && authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedSubject subject
        && subject.role() == Role.ADMIN
        && !subject.guest()) {
      audit.record(SecurityProblemHandlers.adminAction(request));
    }
  }
}
