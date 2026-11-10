package bd.shromikai.api;

import bd.shromikai.auth.AuthService;
import bd.shromikai.auth.Role;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-08 {@code POST /api/v1/auth/demo/{role}}: one-click login for the seeded judge accounts
 * (FR-08). The bean exists only when {@code demo} is active and {@code prod} is not, so the
 * endpoint answers 404 everywhere else.
 */
@RestController
@Profile("demo & !prod")
@RequestMapping("/api/v1/auth/demo")
public class DemoAuthController {

  private final AuthController authController;
  private final AuthService auth;

  public DemoAuthController(ShromikaiProperties properties, AuthService auth) {
    this.authController = new AuthController(auth, properties);
    this.auth = auth;
  }

  @PostMapping("/{role}")
  public ResponseEntity<TokenResponse> demo(@PathVariable String role, HttpServletRequest http) {
    Role parsed;
    try {
      parsed = Role.valueOf(role.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ProblemException(ProblemType.NOT_FOUND);
    }
    return authController.withCookie(auth.demo(parsed, http.getRemoteAddr()));
  }
}
