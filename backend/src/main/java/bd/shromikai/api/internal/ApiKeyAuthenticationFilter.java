package bd.shromikai.api.internal;

import bd.shromikai.auth.ApiKeyPrincipal;
import bd.shromikai.auth.ApiKeyService;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.trace.ActorKind;
import bd.shromikai.trace.AuditAction;
import bd.shromikai.trace.AuditEvent;
import bd.shromikai.trace.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code /mcp} requests by API key (FR-65, ADR-0021): {@code Authorization: Bearer
 * shk_...}. Only an active key counts; anything else presented is refused here with 401 and an
 * {@code ACCESS_DENIED} audit row that carries the reason and never the credential (T-12); rows are
 * limited to 5 a minute per source address ({@link DeniedAuditThrottle}). A request with no
 * credential falls through to the 401 entry point. The key's scopes become the authorities {@code
 * SCOPE_<scope>}.
 */
final class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final String PREFIX = "Bearer ";

  private final ApiKeyService keys;
  private final AuditService audit;
  private final ProblemResponseWriter problems;
  private final DeniedAuditThrottle throttle;

  ApiKeyAuthenticationFilter(
      ApiKeyService keys,
      AuditService audit,
      ProblemResponseWriter problems,
      DeniedAuditThrottle throttle) {
    this.keys = keys;
    this.audit = audit;
    this.problems = problems;
    this.throttle = throttle;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !(path.equals("/mcp") || path.startsWith("/mcp/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || header.isBlank()) {
      chain.doFilter(request, response);
      return;
    }
    Optional<ApiKeyPrincipal> principal =
        header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())
            ? keys.authenticate(header.substring(PREFIX.length()).trim())
            : Optional.empty();
    if (principal.isEmpty()) {
      // The refusal is always answered; the audit row is limited per source address.
      if (throttle.tryAcquire(request.getRemoteAddr())) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("reason", "invalid_key");
        detail.put("httpMethod", request.getMethod());
        detail.put("httpPath", request.getRequestURI());
        audit.record(AuditEvent.of(null, ActorKind.API_KEY, AuditAction.ACCESS_DENIED, detail));
      }
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
      problems.write(request, response, ProblemType.INVALID_TOKEN, null);
      return;
    }
    ApiKeyPrincipal key = principal.get();
    List<GrantedAuthority> authorities =
        key.scopes().stream()
            .sorted()
            .<GrantedAuthority>map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
            .toList();
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(key, null, authorities));
    chain.doFilter(request, response);
  }
}
