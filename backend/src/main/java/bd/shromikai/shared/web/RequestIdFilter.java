package bd.shromikai.shared.web;

import bd.shromikai.shared.logging.LogKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Echoes a well-formed incoming {@code X-Request-Id} or generates one, puts it in the MDC and the
 * response, and exposes it as a request attribute for problem documents (FR-03). An error dispatch
 * keeps the request's original ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

  /** Visible ASCII without separators that could break log lines or headers. */
  private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // An ERROR dispatch reuses the ID of the request that failed, so logs and the problem
    // document carry the same traceId.
    String requestId =
        request.getAttribute(ATTRIBUTE) instanceof String existing
            ? existing
            : resolve(request.getHeader(HEADER));
    request.setAttribute(ATTRIBUTE, requestId);
    response.setHeader(HEADER, requestId);
    MDC.put(LogKeys.REQUEST_ID, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(LogKeys.REQUEST_ID);
    }
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  static String resolve(@Nullable String incoming) {
    if (incoming != null && ACCEPTED.matcher(incoming).matches()) {
      return incoming;
    }
    return UUID.randomUUID().toString();
  }

  /** The request ID of the current request, or a new one if the filter did not run. */
  public static String currentOf(HttpServletRequest request) {
    Object value = request.getAttribute(ATTRIBUTE);
    if (value instanceof String id) {
      return id;
    }
    String generated = resolve(null);
    request.setAttribute(ATTRIBUTE, generated);
    return generated;
  }
}
