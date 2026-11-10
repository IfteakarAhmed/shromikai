package bd.shromikai.api;

import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Test-only endpoints that raise each kind of error for {@code ProblemDetailsIT}. */
@Profile("test")
@RestController
@RequestMapping("/api/v1/test-probe")
class ProblemProbeController {

  record ProbeBody(@NotBlank String name, @Min(1) int count) {}

  @PostMapping("/validate")
  ProbeBody validate(@Valid @RequestBody ProbeBody body) {
    return body;
  }

  @GetMapping("/problem/{code}")
  String problem(@PathVariable String code) {
    throw new ProblemException(ProblemType.valueOf(code), null, 30);
  }

  @GetMapping("/boom")
  String boom() {
    throw new IllegalStateException("secret contract text that must not leak");
  }

  @GetMapping("/conflict")
  String conflict() {
    throw new ResponseStatusException(HttpStatus.CONFLICT);
  }

  @GetMapping("/model-failure")
  String modelFailure() {
    throw new UpstreamUnavailableException("gemini-http-503", 503, 12);
  }
}
