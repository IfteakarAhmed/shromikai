package bd.shromikai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/guest}: the browser's random device token (UUID v4). */
@Schema(name = "GuestRequest")
public record GuestRequest(@NotBlank @Size(max = 64) String deviceToken) {

  @Override
  public String toString() {
    return "GuestRequest[deviceToken=[MASKED]]";
  }
}
