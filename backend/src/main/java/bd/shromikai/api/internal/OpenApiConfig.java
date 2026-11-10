package bd.shromikai.api.internal;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * OpenAPI document at {@code /v3/api-docs}; the frontend generates its types from it (AGENTS.md
 * §6). The server URL is relative so the committed snapshot does not depend on the port.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  @Bean
  OpenAPI shromikaiOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ShromikAI API")
                .version("v1")
                .description(
                    "Verified employment-contract intelligence for Bangladesh. Not legal advice"
                        + " (এটা আইনি পরামর্শ নয়).")
                .license(new License().name("Proprietary")))
        .servers(List.of(new Server().url("/")));
  }

  /**
   * Every response property is present, with {@code null} when it has no value (api.md §1), so each
   * property is listed as required; nullable ones carry {@code nullable: true}.
   */
  @Bean
  OpenApiCustomizer requiredProperties() {
    return openApi -> {
      if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
        return;
      }
      openApi
          .getComponents()
          .getSchemas()
          .values()
          .forEach(
              schema -> {
                if (schema.getProperties() != null) {
                  schema.setRequired(
                      new ArrayList<>(new TreeSet<>(schema.getProperties().keySet())));
                }
              });
      nullableComponents()
          .forEach(
              (schemaName, properties) -> {
                Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
                if (schema != null && schema.getProperties() != null) {
                  properties.forEach(
                      property -> {
                        Schema<?> target = schema.getProperties().get(property);
                        if (target != null) {
                          target.setNullable(true);
                        }
                      });
                }
              });
    };
  }

  /**
   * Record components annotated with JSpecify {@code @Nullable}, per OpenAPI schema name, for every
   * API record that carries {@code @Schema(name = ...)} under {@code bd.shromikai}.
   */
  static Map<String, List<String>> nullableComponents() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
            return definition.getMetadata().isIndependent();
          }
        };
    scanner.addIncludeFilter(
        new AnnotationTypeFilter(io.swagger.v3.oas.annotations.media.Schema.class));
    Map<String, List<String>> result = new TreeMap<>();
    for (BeanDefinition candidate : scanner.findCandidateComponents("bd.shromikai")) {
      Class<?> type =
          ClassUtils.resolveClassName(
              candidate.getBeanClassName(), OpenApiConfig.class.getClassLoader());
      io.swagger.v3.oas.annotations.media.Schema annotation =
          type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
      if (!type.isRecord() || annotation == null || annotation.name().isEmpty()) {
        continue;
      }
      List<String> nullable = new ArrayList<>();
      for (RecordComponent component : type.getRecordComponents()) {
        if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
          nullable.add(component.getName());
        }
      }
      result.put(annotation.name(), nullable);
    }
    return result;
  }
}
