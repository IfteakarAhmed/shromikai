package bd.shromikai.api.internal;

import bd.shromikai.shared.problem.ProblemType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects any request that carries {@code access_token}, {@code token} or {@code api_key} as a
 * query parameter, before authentication, so credentials never reach URLs, access logs or browser
 * history (api.md §2, architecture T-23). The response never echoes the value.
 */
final class CredentialQueryParamFilter extends OncePerRequestFilter {

  static final Set<String> FORBIDDEN = Set.of("access_token", "token", "api_key");

  private final ProblemResponseWriter writer;

  CredentialQueryParamFilter(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    for (String name : FORBIDDEN) {
      if (request.getParameterMap().containsKey(name)) {
        writer.write(
            request,
            response,
            ProblemType.VALIDATION_FAILED,
            "Credentials are accepted only in the Authorization header");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
