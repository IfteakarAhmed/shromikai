package bd.shromikai.api.internal;

import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.trace.ActorKind;
import bd.shromikai.trace.AuditAction;
import bd.shromikai.trace.AuditEvent;
import bd.shromikai.trace.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 401 for a missing or invalid token and 403 for a role that is not allowed, both as problem
 * documents. A 403 also writes an {@code ACCESS_DENIED} audit row (FR-10); it never records the
 * token, only the actor, the method and the path.
 */
@Component
class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ProblemResponseWriter writer;
  private final AuditService audit;

  SecurityProblemHandlers(ProblemResponseWriter writer, AuditService audit) {
    this.writer = writer;
    this.audit = audit;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    writer.write(request, response, ProblemType.INVALID_TOKEN, null);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    audit.record(accessDenied(request));
    writer.write(request, response, ProblemType.FORBIDDEN, null);
  }

  static AuditEvent accessDenied(HttpServletRequest request) {
    return AuditEvent.of(actorId(), actorKind(), AuditAction.ACCESS_DENIED, requestDetail(request));
  }

  static AuditEvent adminAction(HttpServletRequest request) {
    return AuditEvent.of(actorId(), actorKind(), AuditAction.ADMIN_ACTION, requestDetail(request));
  }

  private static Map<String, Object> requestDetail(HttpServletRequest request) {
    Map<String, Object> detail = new HashMap<>();
    String method = request.getMethod();
    if (java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
        .contains(method)) {
      detail.put("httpMethod", method);
    }
    String path = request.getRequestURI();
    if (path != null && path.length() <= 200 && path.matches("/[A-Za-z0-9._~/-]*")) {
      detail.put("httpPath", path);
    }
    AuthenticatedSubject subject = subject();
    if (subject != null) {
      detail.put("role", subject.role().name());
    }
    return detail;
  }

  private static AuthenticatedSubject subject() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof AuthenticatedSubject s
        ? s
        : null;
  }

  private static java.util.UUID actorId() {
    AuthenticatedSubject subject = subject();
    return subject == null ? null : subject.userId();
  }

  private static ActorKind actorKind() {
    AuthenticatedSubject subject = subject();
    return subject != null && subject.guest() ? ActorKind.GUEST : ActorKind.USER;
  }
}
