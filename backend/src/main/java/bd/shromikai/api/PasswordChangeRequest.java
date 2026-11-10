package bd.shromikai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/me/password}; both passwords are masked in logs. */
@Schema(name = "PasswordChangeRequest")
public record PasswordChangeRequest(
    @NotBlank @Size(max = 128) String currentPassword,
    @NotBlank @Size(min = 12, max = 128) String newPassword) {

  @Override
  public String toString() {
    return "PasswordChangeRequest[passwords=[MASKED]]";
  }
}
