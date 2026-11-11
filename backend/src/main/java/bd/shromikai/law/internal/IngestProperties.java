package bd.shromikai.law.internal;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code shromikai.ingest.*}: where the jurisdiction pack, the ILO mapping and the generated report
 * live, and the switches of the {@code ingestLaws} command-line run.
 */
@ConfigurationProperties("shromikai.ingest")
record IngestProperties(
    Path packFile,
    Path mappingFile,
    Path reportDir,
    boolean cli,
    @Nullable List<String> acts,
    @Nullable Long sampleSeed) {}
