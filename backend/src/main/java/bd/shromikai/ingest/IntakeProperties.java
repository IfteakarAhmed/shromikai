package bd.shromikai.ingest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Contract intake limits and the local OCR command (FR-24, FR-27, NFR-20) under {@code
 * shromikai.intake}. Tesseract runs as a child process fed through stdin and stdout: no file is
 * written (R-02).
 */
@Validated
@ConfigurationProperties("shromikai.intake")
public record IntakeProperties(
    @Min(1) long maxBytes,
    @Min(1) int maxPages,
    @Min(1) long maxPixels,
    @Min(1) int maxImageSide,
    Duration pdfTimeout,
    Duration ocrTimeout,
    @Min(0) double minOcrConfidence,
    @Min(1) int ocrParallel,
    @Min(1) int rasterDpi,
    @NotEmpty List<String> tesseractCommand) {

  public static IntakeProperties defaults() {
    return new IntakeProperties(
        10L * 1024 * 1024,
        15,
        36_000_000L,
        6000,
        Duration.ofSeconds(10),
        Duration.ofSeconds(60),
        60.0,
        4,
        200,
        List.of("tesseract", "stdin", "stdout", "-l", "ben+eng", "tsv"));
  }
}
