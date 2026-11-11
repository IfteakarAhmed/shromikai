package bd.shromikai.api.internal;

import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.auth.InvalidTokenException;
import bd.shromikai.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from {@code Authorization: Bearer <access JWT>}. An absent or invalid
 * token leaves the request anonymous; the authorisation rules then answer 401 where a token is
 * required. Guests get the authority {@code ROLE_GUEST}, users {@code ROLE_<role>}.
 */
final class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String PREFIX = "Bearer ";

  private final JwtService jwt;

  JwtAuthenticationFilter(JwtService jwt) {
    this.jwt = jwt;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      chain.doFilter(request, response); // already authenticated, for example by API key
      return;
    }
    if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
      try {
        AuthenticatedSubject subject = jwt.parse(header.substring(PREFIX.length()).trim());
        SecurityContextHolder.getContext()
            .setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    subject, null, authorities(subject)));
      } catch (InvalidTokenException e) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }

  static List<GrantedAuthority> authorities(AuthenticatedSubject subject) {
    return List.of(
        new SimpleGrantedAuthority(
            subject.guest() ? "ROLE_GUEST" : "ROLE_" + subject.role().name()));
  }
}
