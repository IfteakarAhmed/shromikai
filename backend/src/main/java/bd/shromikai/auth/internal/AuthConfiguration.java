package bd.shromikai.auth.internal;

import bd.shromikai.shared.config.ShromikaiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class AuthConfiguration {

  /** BCrypt, strength 12 unless a profile says otherwise (FR-05). */
  @Bean
  PasswordEncoder passwordEncoder(ShromikaiProperties properties) {
    return new BCryptPasswordEncoder(properties.auth().bcryptStrength());
  }
}
