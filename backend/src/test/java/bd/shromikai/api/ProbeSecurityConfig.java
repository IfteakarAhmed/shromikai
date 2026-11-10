package bd.shromikai.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** Leaves the test-only {@code /api/v1/test-probe/**} endpoints open for ProblemDetailsIT. */
@Profile("test")
@Configuration(proxyBeanMethods = false)
class ProbeSecurityConfig {

  @Bean
  @Order(0)
  SecurityFilterChain testProbeChain(HttpSecurity http) throws Exception {
    return http.securityMatcher("/api/v1/test-probe/**")
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .build();
  }
}
