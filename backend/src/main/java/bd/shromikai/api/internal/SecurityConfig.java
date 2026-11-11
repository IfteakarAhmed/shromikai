package bd.shromikai.api.internal;

import bd.shromikai.auth.JwtService;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.trace.AuditService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Stateless security chain: bearer JWTs only, no session, no CSRF cookie (nothing authenticates by
 * cookie except the refresh endpoint, which is SameSite=Strict and CORS-limited). The access matrix
 * is api.md §2; anything not listed requires a signed-in caller (FR-06).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      JwtService jwt,
      AuditService audit,
      ProblemResponseWriter problemWriter,
      SecurityProblemHandlers problems,
      ShromikaiProperties properties)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .headers(
            headers -> {
              headers.addHeaderWriter(contentSecurityPolicy());
              headers.referrerPolicy(
                  r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
              if (properties.auth().hsts()) {
                headers.httpStrictTransportSecurity(
                    hsts ->
                        hsts.maxAgeInSeconds(31_536_000)
                            .includeSubDomains(true)
                            .requestMatcher(r -> true));
              } else {
                headers.httpStrictTransportSecurity(hsts -> hsts.disable());
              }
            })
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(
                        DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC)
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    // Public (api.md §2).
                    .requestMatchers(HttpMethod.GET, "/api/v1/meta")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/guest",
                        "/api/v1/auth/demo/*")
                    .permitAll()
                    // Account changes are for real accounts, never guests.
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/me/password")
                    .hasAnyRole("WORKER", "HR", "ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/me")
                    .hasAnyRole("WORKER", "HR", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/me")
                    .hasAnyRole("WORKER", "HR", "ADMIN")
                    .requestMatchers("/api/v1/auth/**")
                    .hasAnyRole("GUEST", "WORKER", "HR", "ADMIN")
                    // Role-protected groups.
                    .requestMatchers(
                        "/api/v1/admin/**", "/api/v1/review-items/**", "/api/v1/dev/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/playbooks/**")
                    .hasAnyRole("HR", "ADMIN")
                    .requestMatchers(
                        "/api/v1/laws/**",
                        "/api/v1/analyses/**",
                        "/api/v1/compare/**",
                        "/api/v1/memos/**",
                        "/api/v1/review-tickets/**")
                    .hasAnyRole("GUEST", "WORKER", "HR", "ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new CredentialQueryParamFilter(problemWriter), SecurityContextHolderFilter.class)
        .addFilterBefore(
            new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new AdminActionAuditFilter(audit), AuthorizationFilter.class);
    return http.build();
  }

  /**
   * API responses get {@code default-src 'self'; frame-ancestors 'none'} with no inline script. The
   * Swagger UI page needs inline styles, so it alone also allows {@code style-src 'unsafe-inline'}
   * and data images (FR-11).
   */
  private static HeaderWriter contentSecurityPolicy() {
    return (request, response) -> {
      String path = request.getRequestURI();
      boolean swagger = path.startsWith("/swagger-ui");
      response.setHeader(
          "Content-Security-Policy",
          swagger
              ? "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;"
                  + " frame-ancestors 'none'"
              : "default-src 'self'; frame-ancestors 'none'");
    };
  }
}
