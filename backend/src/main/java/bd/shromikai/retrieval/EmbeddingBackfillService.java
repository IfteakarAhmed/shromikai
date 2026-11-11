package bd.shromikai.retrieval;

import bd.shromikai.retrieval.internal.RetrievalProperties;
import bd.shromikai.retrieval.internal.SectionSearchRepository;
import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.ai.EmbeddingPort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * One embedding per section version (FR-18): the input is both headings plus the authoritative
 * text, embedded in batches. A section whose {@code content_hash} equals the hash its embedding was
 * computed from is skipped; a changed section or a changed model is embedded again.
 */
@Service
public class EmbeddingBackfillService {

  /** About 8,000 bge-m3 tokens of Bangla; longer inputs are cut and flagged. */
  static final int MAX_INPUT_CHARS = 16_000;

  private record Candidate(long id, String contentHash, String input, boolean truncated) {}

  private final JdbcClient jdbc;
  private final EmbeddingPort embeddings;
  private final int batchSize;

  EmbeddingBackfillService(
      JdbcClient jdbc, EmbeddingPort embeddings, RetrievalProperties properties) {
    this.jdbc = jdbc;
    this.embeddings = embeddings;
    this.batchSize = properties.embedBatchSize();
  }

  /** Embeds every section version that has no up-to-date embedding. Safe to run repeatedly. */
  public BackfillResult run() {
    List<Candidate> pending = new ArrayList<>();
    int skipped = 0;
    for (Candidate candidate : load()) {
      if (candidate == null) {
        skipped++;
      } else {
        pending.add(candidate);
      }
    }
    int truncated = 0;
    for (int from = 0; from < pending.size(); from += batchSize) {
      List<Candidate> batch = pending.subList(from, Math.min(pending.size(), from + batchSize));
      List<float[]> vectors = embeddings.embed(batch.stream().map(Candidate::input).toList());
      if (vectors.size() != batch.size()) {
        throw new IllegalStateException("embedding port returned a wrong number of vectors");
      }
      for (int i = 0; i < batch.size(); i++) {
        Candidate c = batch.get(i);
        float[] vector = vectors.get(i);
        if (vector.length != embeddings.dimensions()) {
          throw new IllegalStateException("embedding has the wrong number of dimensions");
        }
        store(c, vector);
        if (c.truncated()) {
          truncated++;
        }
      }
    }
    return new BackfillResult(pending.size(), skipped, truncated);
  }

  /** One entry per section version; {@code null} marks an up-to-date section. */
  private List<Candidate> load() {
    String model = embeddings.modelId();
    return jdbc.sql(
            """
            select s.id, s.content_hash, s.heading_bn, s.heading_en,
                   case a.authoritative_lang when 'bn' then s.text_bn else s.text_en end as body,
                   (s.embedding is not null and s.embedded_model = :model
                      and s.embedded_hash is not null) as embedded,
                   s.embedded_hash
              from law_section s
              join law_act a on a.id = s.act_id
             order by s.id
            """)
        .param("model", model)
        .query(
            (rs, row) -> {
              String contentHash = rs.getString("content_hash");
              boolean embedded = rs.getBoolean("embedded");
              if (embedded
                  && contentHash != null
                  && contentHash.equals(rs.getString("embedded_hash"))) {
                return null;
              }
              String input =
                  input(
                      rs.getString("heading_bn"), rs.getString("heading_en"), rs.getString("body"));
              boolean truncated = input.length() > MAX_INPUT_CHARS;
              if (truncated) {
                input = input.substring(0, MAX_INPUT_CHARS);
              }
              String hash = contentHash != null ? contentHash : Hashing.sha256Hex(input);
              if (embedded && contentHash == null && hash.equals(rs.getString("embedded_hash"))) {
                return null;
              }
              return new Candidate(rs.getLong("id"), hash, input, truncated);
            })
        .list();
  }

  static String input(String headingBn, String headingEn, String body) {
    return nullToEmpty(headingBn) + " | " + nullToEmpty(headingEn) + "\n" + nullToEmpty(body);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private void store(Candidate c, float[] vector) {
    jdbc.sql(
            """
            update law_section
               set embedding = cast(:vector as vector), embedded_hash = :hash,
                   embedded_model = :model, embedding_truncated = :truncated
             where id = :id
            """)
        .param("vector", SectionSearchRepository.literal(vector))
        .param("hash", c.contentHash())
        .param("model", embeddings.modelId())
        .param("truncated", c.truncated())
        .param("id", c.id())
        .update();
  }
}
