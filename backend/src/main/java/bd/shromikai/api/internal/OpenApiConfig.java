package bd.shromikai.api.internal;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
      Set<String> fromFiles = ApiSchemaComponents.all().keySet();
      openApi
          .getComponents()
          .getSchemas()
          .forEach(
              (name, schema) -> {
                // Schemas from the JSON Schema files keep their own required lists (FR-63).
                if (schema.getProperties() != null && !fromFiles.contains(name)) {
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
                        if (target != null && target.get$ref() != null) {
                          // OpenAPI 3.0 ignores siblings of $ref: wrap it to carry nullable.
                          ComposedSchema wrapped = new ComposedSchema();
                          wrapped.addAllOfItem(new Schema<>().$ref(target.get$ref()));
                          wrapped.setNullable(true);
                          schema.getProperties().put(property, wrapped);
                        } else if (target != null) {
                          target.setNullable(true);
                        }
                      });
                }
              });
      addProblem(openApi);
    };
  }

  /**
   * Response and request schemas of api.md §5 (FR-63): the JSON Schema files under {@code
   * schemas/api} become components, and the operations that return them say so, so the generated
   * frontend types carry the real shapes instead of an untyped object.
   */
  @Bean
  OpenApiCustomizer apiSchemas() {
    return openApi -> {
      if (openApi.getComponents() == null) {
        return;
      }
      ApiSchemaComponents.all()
          .forEach((name, json) -> openApi.getComponents().addSchemas(name, parse(json)));
      // The profile of an account is a map in Java; its fields are the StoredProfile schema.
      for (String name : List.of("Me", "MeUpdate")) {
        Schema<?> owner = openApi.getComponents().getSchemas().get(name);
        if (owner != null
            && owner.getProperties() != null
            && owner.getProperties().containsKey("profile")) {
          ComposedSchema profile = new ComposedSchema();
          profile.addAllOfItem(new Schema<>().$ref("#/components/schemas/StoredProfile"));
          profile.setNullable(true);
          owner.getProperties().put("profile", profile);
        }
      }
      // Replaced by ReviewItemPatch; the Java record's schema would be an orphan.
      if (openApi.getComponents().getSchemas() != null) {
        openApi.getComponents().getSchemas().remove("Patch");
      }
      if (openApi.getPaths() == null) {
        return;
      }
      RESPONSES.forEach(
          (operationKey, responses) -> {
            String[] parts = operationKey.split(" ", 2);
            var item = openApi.getPaths().get(parts[1]);
            if (item == null) {
              return;
            }
            Operation operation =
                item.readOperationsMap().get(PathItem.HttpMethod.valueOf(parts[0]));
            if (operation == null) {
              return;
            }
            responses.forEach(
                (status, components) -> {
                  ApiResponse response = operation.getResponses().get(status);
                  if (response == null) {
                    response = new ApiResponse().description(status + " response");
                    operation.getResponses().addApiResponse(status, response);
                  }
                  response.setContent(
                      new Content()
                          .addMediaType(
                              "application/json", new MediaType().schema(schemaOf(components))));
                });
          });
      REQUESTS.forEach(
          (operationKey, component) -> {
            String[] parts = operationKey.split(" ", 2);
            var item = openApi.getPaths().get(parts[1]);
            Operation operation =
                item == null
                    ? null
                    : item.readOperationsMap().get(PathItem.HttpMethod.valueOf(parts[0]));
            if (operation != null) {
              operation.setRequestBody(
                  new RequestBody()
                      .required(true)
                      .content(
                          new Content()
                              .addMediaType(
                                  "application/json",
                                  new MediaType().schema(schemaOf(component)))));
            }
          });
    };
  }

  /** Operation to status to component name(s); {@code A|B} is a {@code oneOf}. */
  private static final Map<String, Map<String, String>> RESPONSES =
      Map.ofEntries(
          Map.entry(
              "POST /api/v1/analyses", Map.of("200", "AnalysisResult", "202", "AnalysisAccepted")),
          Map.entry(
              "GET /api/v1/analyses/{id}",
              Map.of("200", "AnalysisResult|AnalysisStatusView", "202", "AnalysisStatusView")),
          Map.entry("GET /api/v1/analyses/{id}/trace", Map.of("200", "AnalysisTrace")),
          Map.entry("GET /api/v1/analyses/{id}/obligations", Map.of("200", "ObligationsExport")),
          Map.entry("POST /api/v1/compare", Map.of("202", "CompareAccepted")),
          Map.entry(
              "GET /api/v1/compare/{id}", Map.of("200", "CompareResult", "202", "CompareRunning")),
          Map.entry("GET /api/v1/review-tickets/{ticketId}", Map.of("200", "ReviewTicketStatus")),
          Map.entry("GET /api/v1/review-items", Map.of("200", "ReviewItemPage")),
          Map.entry("GET /api/v1/review-items/{id}", Map.of("200", "ReviewItem")),
          Map.entry("PATCH /api/v1/review-items/{id}", Map.of("200", "ReviewItem")),
          Map.entry("GET /api/v1/admin/usage", Map.of("200", "UsageReport")),
          Map.entry("GET /api/v1/admin/traces/{analysisId}", Map.of("200", "AnalysisTrace")));

  private static final Map<String, String> REQUESTS =
      Map.of("PATCH /api/v1/review-items/{id}", "ReviewItemPatch");

  private static Schema<?> schemaOf(String components) {
    String[] names = components.split("\\|");
    if (names.length == 1) {
      return new Schema<>().$ref("#/components/schemas/" + names[0]);
    }
    ComposedSchema union = new ComposedSchema();
    for (String name : names) {
      union.addOneOfItem(new Schema<>().$ref("#/components/schemas/" + name));
    }
    return union;
  }

  private static Schema<?> parse(com.fasterxml.jackson.databind.JsonNode json) {
    try {
      return io.swagger.v3.core.util.Json.mapper().treeToValue(json, Schema.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Invalid API schema component", e);
    }
  }

  static final String PROBLEM_JSON = "application/problem+json";

  /**
   * The {@code Problem} schema of api.md §5.14, built by hand because responses carry Spring's
   * {@code ProblemDetail}. It keeps its own required list (optional members are absent, not null),
   * and every operation documents it as the {@code default} response (FR-02).
   */
  static void addProblem(OpenAPI openApi) {
    Schema<?> error =
        new ObjectSchema()
            .addProperty("pointer", new StringSchema())
            .addProperty("message", new StringSchema());
    error.setRequired(List.of("message", "pointer"));
    Schema<?> problem =
        new ObjectSchema()
            .description("RFC 9457 problem document (api.md §5.14)")
            .addProperty("type", new StringSchema().pattern("^urn:shromikai:problem:[a-z0-9-]+$"))
            .addProperty("title", new StringSchema().description("Localised by Accept-Language"))
            .addProperty(
                "status",
                new IntegerSchema()
                    .minimum(BigDecimal.valueOf(400))
                    .maximum(BigDecimal.valueOf(599)))
            .addProperty("detail", new StringSchema().nullable(true))
            .addProperty(
                "instance", new StringSchema().description("Request path template plus request ID"))
            .addProperty(
                "code",
                new StringSchema()
                    .description("Stable machine code; equals the last segment of type"))
            .addProperty("traceId", new StringSchema())
            .addProperty(
                "errors",
                new ArraySchema().items(new Schema<>().$ref("#/components/schemas/ProblemError")))
            .addProperty(
                "retryAfterSeconds", new IntegerSchema().minimum(BigDecimal.ZERO).nullable(true))
            .addProperty(
                "pages",
                new ArraySchema()
                    .items(new IntegerSchema().minimum(BigDecimal.ONE))
                    .description("unreadable-document only"));
    problem.setRequired(List.of("code", "instance", "status", "title", "traceId", "type"));
    openApi.getComponents().addSchemas("ProblemError", error);
    openApi.getComponents().addSchemas("Problem", problem);
    if (openApi.getPaths() == null) {
      return;
    }
    openApi
        .getPaths()
        .values()
        .forEach(
            item ->
                item.readOperations()
                    .forEach(
                        operation ->
                            operation
                                .getResponses()
                                .addApiResponse(
                                    "default",
                                    new ApiResponse()
                                        .description("Problem document")
                                        .content(
                                            new Content()
                                                .addMediaType(
                                                    PROBLEM_JSON,
                                                    new MediaType()
                                                        .schema(
                                                            new Schema<>()
                                                                .$ref(
                                                                    "#/components/schemas/Problem")))))));
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
