package bd.shromikai.retrieval.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Retrieval settings under {@code shromikai.retrieval} (architecture §12). */
@Validated
@ConfigurationProperties("shromikai.retrieval")
public record RetrievalProperties(
    @Min(1) int vectorTopK,
    @Min(1) int textTopK,
    @Min(1) int fusedTopK,
    @Min(1) int rrfK,
    @Min(0) int graphMax,
    @Min(0) int topicMapMax,
    @Min(1) int efSearch,
    @Min(1) int embedBatchSize,
    @Valid @NotNull Cache cache,
    @NotBlank String topicMapFile,
    boolean cli,
    @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") double textMaxDocFreq,
    boolean textLanguageFilter) {

  public record Cache(@Min(1) long maxEntries, @NotNull Duration ttl) {}
}
