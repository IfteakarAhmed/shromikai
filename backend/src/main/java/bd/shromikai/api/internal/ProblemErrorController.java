package bd.shromikai.api.internal;

import bd.shromikai.shared.logging.LogKeys;
import bd.shromikai.shared.problem.ProblemType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's {@code /error} page so errors raised outside MVC (filters, the servlet
 * container) are problem documents too (FR-02).
 */
@RestController
public class ProblemErrorController implements ErrorController {

  private static final Logger log = LoggerFactory.getLogger(ProblemErrorController.class);

  @RequestMapping("${server.error.path:/error}")
  ResponseEntity<Object> error(HttpServletRequest request) {
    Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = code instanceof Integer value ? value : 500;
    if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable error) {
      log.atError()
          .addKeyValue(LogKeys.HTTP_STATUS, status)
          .addKeyValue(LogKeys.REASON_CODE, error.getClass().getSimpleName())
          .log("Error outside the MVC handler");
    }
    return ProblemFactory.response(
        ProblemFactory.build(ProblemType.forStatus(status), null, request, status), null);
  }
}
