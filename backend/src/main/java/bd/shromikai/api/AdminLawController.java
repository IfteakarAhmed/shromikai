package bd.shromikai.api;

import bd.shromikai.law.LawIngestService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** E-41 {@code POST /api/v1/admin/laws/ingest} (ADMIN): starts a statute ingestion (FR-15). */
@RestController
@RequestMapping("/api/v1/admin/laws")
public class AdminLawController {

  private final LawIngestService ingest;

  public AdminLawController(LawIngestService ingest) {
    this.ingest = ingest;
  }

  /** Optional list of act codes of the pack; all acts when absent. */
  @Schema(name = "IngestRequest")
  public record IngestRequest(
      @Nullable @Size(max = 20)
          List<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9-]{1,31}$") String> acts) {}

  @Schema(name = "IngestAccepted")
  public record IngestAccepted(UUID ingestRunId) {}

  @PostMapping("/ingest")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public IngestAccepted ingest(
      @Valid @RequestBody(required = false) @Nullable IngestRequest request) {
    List<String> acts = request == null ? null : request.acts();
    return new IngestAccepted(
        ingest.startAsync(acts == null || acts.isEmpty() ? null : new HashSet<>(acts)));
  }
}
