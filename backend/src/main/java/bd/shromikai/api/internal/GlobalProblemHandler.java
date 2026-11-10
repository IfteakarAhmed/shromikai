package bd.shromikai.api.internal;

import bd.shromikai.shared.ai.ModelException;
import bd.shromikai.shared.logging.LogKeys;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into {@code application/problem+json} (FR-02). Bodies never contain stack
 * traces; logs carry only the problem code and exception class (R-02).
 */
@RestControllerAdvice
public class GlobalProblemHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalProblemHandler.class);

  @ExceptionHandler(ProblemException.class)
  ResponseEntity<Object> handleProblem(ProblemException e, HttpServletRequest request) {
    if (e.type().status() >= 500) {
      log.atWarn()
          .addKeyValue(LogKeys.ERROR_CODE, e.type().code())
          .addKeyValue(
              LogKeys.REASON_CODE,
              e instanceof ModelException model ? model.reasonCode() : e.getClass().getSimpleName())
          .log("Request failed");
    }
    return ProblemFactory.response(
        ProblemFactory.build(e.type(), e.detail(), request), e.retryAfterSeconds());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<Object> handleConstraintViolation(
      ConstraintViolationException e, HttpServletRequest request) {
    ProblemDetail problem = ProblemFactory.build(ProblemType.VALIDATION_FAILED, null, request);
    List<Map<String, String>> errors = new ArrayList<>();
    e.getConstraintViolations()
        .forEach(
            violation ->
                errors.add(
                    error(
                        "/" + violation.getPropertyPath().toString().replace('.', '/'),
                        violation.getMessage())));
    problem.setProperty("errors", errors);
    return ProblemFactory.response(problem, null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception e, HttpServletRequest request) {
    log.atError()
        .addKeyValue(LogKeys.ERROR_CODE, ProblemType.INTERNAL_ERROR.code())
        .addKeyValue(LogKeys.REASON_CODE, e.getClass().getSimpleName())
        .log("Unhandled exception");
    return ProblemFactory.response(
        ProblemFactory.build(ProblemType.INTERNAL_ERROR, null, request), null);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemFactory.build(ProblemType.VALIDATION_FAILED, null, servletRequest(request));
    List<Map<String, String>> errors = new ArrayList<>();
    for (FieldError field : e.getBindingResult().getFieldErrors()) {
      errors.add(
          error("/" + field.getField().replace('.', '/'), message(field.getDefaultMessage())));
    }
    e.getBindingResult()
        .getGlobalErrors()
        .forEach(global -> errors.add(error("", message(global.getDefaultMessage()))));
    problem.setProperty("errors", errors);
    return ProblemFactory.response(problem, null);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemFactory.build(ProblemType.VALIDATION_FAILED, null, servletRequest(request));
    List<Map<String, String>> errors = new ArrayList<>();
    e.getParameterValidationResults()
        .forEach(
            result ->
                result
                    .getResolvableErrors()
                    .forEach(
                        error ->
                            errors.add(
                                error(
                                    "/" + result.getMethodParameter().getParameterName(),
                                    message(error.getDefaultMessage())))));
    problem.setProperty("errors", errors);
    return ProblemFactory.response(problem, null);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemFactory.build(ProblemType.VALIDATION_FAILED, null, servletRequest(request));
    problem.setProperty("errors", List.of(error("", "Request body is not valid JSON")));
    return ProblemFactory.response(problem, null);
  }

  /**
   * Every other framework exception keeps its own status (for example a {@code
   * ResponseStatusException(409)}) and gets the matching problem code; a status without a specific
   * code gets {@code validation-failed} (4xx) or {@code internal-error} (5xx).
   */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail problem =
        ProblemFactory.build(
            ProblemType.forStatus(status.value()), null, servletRequest(request), status.value());
    HttpHeaders filtered = new HttpHeaders();
    if (headers.getAllow() != null && !headers.getAllow().isEmpty()) {
      filtered.setAllow(headers.getAllow());
    }
    ResponseEntity<Object> response = ProblemFactory.response(problem, null);
    return ResponseEntity.status(response.getStatusCode())
        .headers(response.getHeaders())
        .headers(filtered)
        .body(response.getBody());
  }

  private static HttpServletRequest servletRequest(WebRequest request) {
    return ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
  }

  private static Map<String, String> error(String pointer, String message) {
    return Map.of("pointer", pointer, "message", message);
  }

  private static String message(@Nullable String message) {
    return message == null ? "invalid" : message;
  }
}
