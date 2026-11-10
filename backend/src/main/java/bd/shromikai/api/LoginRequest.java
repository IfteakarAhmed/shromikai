package bd.shromikai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/login}. The password is masked in {@code toString()}. */
@Schema(name = "LoginRequest")
public record LoginRequest(
    @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {

  @Override
  public String toString() {
    return "LoginRequest[email=[MASKED], password=[MASKED]]";
  }
}
