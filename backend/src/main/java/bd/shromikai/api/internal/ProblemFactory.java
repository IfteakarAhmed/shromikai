package bd.shromikai.api.internal;

import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Builds RFC 9457 problem documents with the fields of the {@code Problem} schema (api.md §5.14).
 */
final class ProblemFactory {

  static final String UNMATCHED_PATH = "/unmatched";
  private static final List<Locale> SUPPORTED =
      List.of(Locale.forLanguageTag("bn"), Locale.ENGLISH);

  private ProblemFactory() {}

  static ProblemDetail build(
      ProblemType type, @Nullable String detail, HttpServletRequest request) {
    String requestId = RequestIdFilter.currentOf(request);
    ProblemDetail problem = ProblemDetail.forStatus(type.status());
    problem.setType(URI.create(type.typeUri()));
    problem.setTitle(type.title(language(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE))));
    problem.setDetail(detail);
    problem.setInstance(instance(pathTemplate(request), requestId));
    problem.setProperty("code", type.code());
    problem.setProperty("traceId", requestId);
    return problem;
  }

  static ResponseEntity<Object> response(ProblemDetail problem, @Nullable Integer retryAfter) {
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(HttpStatusCode.valueOf(problem.getStatus()))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (retryAfter != null) {
      problem.setProperty("retryAfterSeconds", retryAfter);
      builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
    }
    return builder.body(problem);
  }

  /** Path template plus request ID; template braces are percent-encoded by {@link URI}. */
  static URI instance(String pathTemplate, String requestId) {
    try {
      return new URI(null, null, pathTemplate, requestId);
    } catch (URISyntaxException e) {
      return URI.create(UNMATCHED_PATH);
    }
  }

  /** Bangla unless the client prefers English (api.md §1). */
  static Locale language(@Nullable String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return SUPPORTED.get(0);
    }
    try {
      Locale match = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED);
      return match == null ? SUPPORTED.get(0) : match;
    } catch (IllegalArgumentException e) {
      return SUPPORTED.get(0);
    }
  }

  /** The matched route template; never the raw path, which could carry identifiers. */
  static String pathTemplate(HttpServletRequest request) {
    if (request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE) != null) {
      return UNMATCHED_PATH;
    }
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern instanceof String template && !template.startsWith("/**")) {
      return template;
    }
    return UNMATCHED_PATH;
  }
}
