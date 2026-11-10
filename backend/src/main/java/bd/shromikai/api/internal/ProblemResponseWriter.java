package bd.shromikai.api.internal;

import bd.shromikai.shared.problem.ProblemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Writes {@code application/problem+json} from servlet filters, where the MVC exception handler
 * does not run (security entry points, the query-credential filter).
 */
@Component
class ProblemResponseWriter {

  private final ObjectMapper mapper;

  ProblemResponseWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  void write(
      HttpServletRequest request,
      HttpServletResponse response,
      ProblemType type,
      @Nullable String detail)
      throws IOException {
    ProblemDetail problem = ProblemFactory.build(type, detail, request);
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    mapper.writeValue(response.getOutputStream(), problem);
  }
}
