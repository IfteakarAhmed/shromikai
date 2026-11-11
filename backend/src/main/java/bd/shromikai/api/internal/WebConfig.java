package bd.shromikai.api.internal;

import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.web.RequestIdFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the web app (conflict C10: all methods the API uses). */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

  private final ShromikaiProperties properties;

  public WebConfig(ShromikaiProperties properties) {
    this.properties = properties;
  }

  /** Results, tickets, memos and auth answers are never cached (api.md §1). */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new org.springframework.web.servlet.HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  jakarta.servlet.http.HttpServletRequest request,
                  jakarta.servlet.http.HttpServletResponse response,
                  Object handler) {
                response.setHeader("Cache-Control", "no-store");
                return true;
              }
            })
        .addPathPatterns(
            "/api/v1/analyses/**",
            "/api/v1/compare/**",
            "/api/v1/memos/**",
            "/api/v1/review-tickets/**");
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
