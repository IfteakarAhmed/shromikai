package bd.shromikai.shared.ai.ollama;

import java.util.Set;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.health.AdditionalHealthEndpointPath;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.boot.actuate.health.StatusAggregator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps local-model state out of the public {@code /actuator/health} (liveness, FR-04). Ollama is
 * reported in the {@code dependencies} group ({@code /actuator/health/dependencies}) instead; an
 * unreachable Ollama must not take the app's public health DOWN.
 */
@Configuration(proxyBeanMethods = false)
class PublicHealthConfig {

  private static final Set<String> DEPENDENCY_ONLY = Set.of("ollama");

  @Bean
  static BeanPostProcessor primaryHealthGroupWithoutDependencies() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof HealthEndpointGroups groups) {
          return new FilteredPrimary(groups);
        }
        return bean;
      }
    };
  }

  private record FilteredPrimary(HealthEndpointGroups delegate) implements HealthEndpointGroups {

    @Override
    public HealthEndpointGroup getPrimary() {
      return new Filtered(delegate.getPrimary());
    }

    @Override
    public Set<String> getNames() {
      return delegate.getNames();
    }

    @Override
    public HealthEndpointGroup get(String name) {
      return delegate.get(name);
    }
  }

  private record Filtered(HealthEndpointGroup delegate) implements HealthEndpointGroup {

    @Override
    public boolean isMember(String name) {
      return !DEPENDENCY_ONLY.contains(name) && delegate.isMember(name);
    }

    @Override
    public boolean showComponents(SecurityContext securityContext) {
      return delegate.showComponents(securityContext);
    }

    @Override
    public boolean showDetails(SecurityContext securityContext) {
      return delegate.showDetails(securityContext);
    }

    @Override
    public StatusAggregator getStatusAggregator() {
      return delegate.getStatusAggregator();
    }

    @Override
    public HttpCodeStatusMapper getHttpCodeStatusMapper() {
      return delegate.getHttpCodeStatusMapper();
    }

    @Override
    public AdditionalHealthEndpointPath getAdditionalPath() {
      return delegate.getAdditionalPath();
    }
  }
}
