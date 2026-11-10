package bd.shromikai.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShromikaiProperties.class)
public class SharedConfiguration {

  /** Runs the startup guards as soon as the properties are bound. */
  @Bean
  StartupGuardMarker startupGuards(Environment environment, ShromikaiProperties properties) {
    StartupGuards.check(environment, properties);
    return new StartupGuardMarker();
  }

  /** Bean whose existence proves the guards ran. */
  public static final class StartupGuardMarker {}
}
