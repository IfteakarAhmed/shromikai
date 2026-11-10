package bd.shromikai.api.internal;

import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.web.RequestIdFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the web app (conflict C10: all methods the API uses). */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

  private final ShromikaiProperties properties;

  public WebConfig(ShromikaiProperties properties) {
    this.properties = properties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
        .exposedHeaders(RequestIdFilter.HEADER, "Retry-After")
        .allowCredentials(true);
  }
}
