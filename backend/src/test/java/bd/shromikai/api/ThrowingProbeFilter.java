package bd.shromikai.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Test-only filter that records the request ID it sees in the MDC and then fails, so {@code
 * ProblemDetailsIT} can check that the error dispatch keeps the same request ID (FR-03).
 */
@Profile("test")
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ThrowingProbeFilter extends OncePerRequestFilter {

  static final String PATH = "/api/v1/test-probe/filter-boom";
  private static final AtomicReference<@Nullable String> LAST = new AtomicReference<>();

  static @Nullable String lastRequestId() {
    return LAST.get();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !PATH.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException {
    LAST.set(MDC.get("requestId"));
    throw new ServletException("probe filter failure");
  }
}
