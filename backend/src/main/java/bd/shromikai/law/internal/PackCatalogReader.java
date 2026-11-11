package bd.shromikai.law.internal;

import bd.shromikai.law.ActSummary;
import bd.shromikai.law.PackInfo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads the pack label from the manifest and its coverage from the corpus tables, for MCP {@code
 * list_jurisdiction_packs} (FR-64). Nothing here is legal content: names come from the manifest,
 * counts from the database.
 */
@Component
public class PackCatalogReader {

  private final IngestProperties ingest;
  private final LawReadRepository repository;
  private final JdbcClient jdbc;

  PackCatalogReader(IngestProperties ingest, LawReadRepository repository, JdbcClient jdbc) {
    this.ingest = ingest;
    this.repository = repository;
    this.jdbc = jdbc;
  }

  /** The pack of the configured manifest; the code falls back to {@code BD}. */
  public List<PackInfo> packs() {
    String code = "BD";
    String nameEn = "Bangladesh";
    String nameBn = null;
    if (Files.isReadable(ingest.packFile())) {
      try (InputStream in = Files.newInputStream(ingest.packFile())) {
        Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        if (root instanceof Map<?, ?> map) {
          code = text(map.get("code"), code);
          nameEn = text(map.get("name_en"), nameEn);
          nameBn = text(map.get("name_bn"), null);
        }
      } catch (IOException | RuntimeException e) {
        // an unreadable manifest only costs the label; the coverage below still holds
      }
    }
    List<ActSummary> acts = repository.acts(List.of(code));
    List<ActSummary> ilo = repository.acts(List.of("ILO"));
    long reviewed =
        jdbc.sql("select count(*) from section_edge where source = 'MAPPING' and reviewed")
            .query(Long.class)
            .single();
    return List.of(new PackInfo(code, nameEn, nameBn, acts, ilo, reviewed));
  }

  private static String text(Object value, String fallback) {
    return value == null || value.toString().isBlank() ? fallback : value.toString().strip();
  }
}
