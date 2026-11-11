package bd.shromikai.retrieval.internal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** All SQL of retrieval (module {@code retrieval}); JdbcClient only (AGENTS.md §6). */
@Repository
public class SectionSearchRepository {

  private final JdbcClient jdbc;
  private final TransactionTemplate transaction;
  private final RetrievalProperties properties;

  SectionSearchRepository(
      JdbcClient jdbc, TransactionTemplate transaction, RetrievalProperties properties) {
    this.jdbc = jdbc;
    this.transaction = transaction;
    this.properties = properties;
  }

  /**
   * Current sections of the jurisdictions ordered by cosine distance to {@code vector}, best first.
   * HNSW runs with {@code ef_search} from the settings and iterative scan, so the current and
   * jurisdiction filters do not starve the result.
   */
  public List<Long> vectorSearch(float[] vector, Collection<String> jurisdictions, int limit) {
    if (jurisdictions.isEmpty()) {
      return List.of();
    }
    return transaction.execute(
        status -> {
          jdbc.sql("select set_config('hnsw.ef_search', :ef, true)")
              .param("ef", Integer.toString(properties.efSearch()))
              .query()
              .listOfRows();
          jdbc.sql("select set_config('hnsw.iterative_scan', 'relaxed_order', true)")
              .query()
              .listOfRows();
          return jdbc.sql(
                  """
                  select s.id
                    from law_section s
                    join law_act a on a.id = s.act_id
                   where s.is_current
                     and s.embedding is not null
                     and a.jurisdiction in (:jurisdictions)
                   order by s.embedding <=> cast(:vector as vector), s.id
                   limit :limit
                  """)
              .param("jurisdictions", jurisdictions)
              .param("vector", literal(vector))
              .param("limit", limit)
              .query(Long.class)
              .list();
        });
  }

  /** Lexemes PostgreSQL extracts from the (normalised) text, so query and index tokenise alike. */
  public List<String> lexemes(String normalisedText) {
    if (normalisedText.isBlank()) {
      return List.of();
    }
    return jdbc.sql("select lexeme from unnest(to_tsvector('simple', :text)) order by lexeme")
        .param("text", normalisedText)
        .query(String.class)
        .list();
  }

  /**
   * Current sections matching any of the lexemes in the normalised text, best {@code ts_rank_cd}
   * first (an OR query; ADR-0012).
   *
   * @param lexemes the words of the query after any filtering; they contain no whitespace
   * @param language when not {@code null}, only sections of acts whose authoritative language is
   *     this one
   */
  public List<Long> textSearch(
      List<String> lexemes, Collection<String> jurisdictions, int limit, String language) {
    if (jurisdictions.isEmpty() || lexemes.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            with q as (
              select to_tsquery('simple',
                       (select string_agg(quote_literal(l), ' | ')
                          from unnest(string_to_array(:lexemes, ' ')) l)) as tsq)
            select s.id
              from law_section s
              join law_act a on a.id = s.act_id
              cross join q
             where s.is_current
               and a.jurisdiction in (:jurisdictions)
               and (cast(:language as text) is null or a.authoritative_lang = cast(:language as text))
               and to_tsvector('simple', coalesce(s.normalised_text, '')) @@ q.tsq
             order by ts_rank_cd(to_tsvector('simple', coalesce(s.normalised_text, '')), q.tsq) desc,
                      s.id
             limit :limit
            """)
        .param("lexemes", String.join(" ", lexemes))
        .param("jurisdictions", jurisdictions)
        .param("language", language)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }

  /** Number of current sections each lexeme occurs in, and the number of current sections. */
  public DocFreq docFreq() {
    Map<String, Integer> counts = new java.util.HashMap<>();
    jdbc.sql(
            """
            select word, ndoc from ts_stat($$
              select to_tsvector('simple', coalesce(normalised_text, ''))
                from law_section where is_current $$)
            """)
        .query((rs, row) -> counts.put(rs.getString("word"), rs.getInt("ndoc")))
        .list();
    long sections =
        jdbc.sql("select count(*) from law_section where is_current").query(Long.class).single();
    return new DocFreq(Map.copyOf(counts), sections);
  }

  /** Document frequencies of the current corpus. */
  public record DocFreq(Map<String, Integer> counts, long sections) {}

  /** Display columns of the given sections, keyed by ID. */
  public Map<Long, SectionRow> load(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<Long, SectionRow> out = new LinkedHashMap<>();
    jdbc.sql(
            """
            select s.id, s.section_key, a.code, s.section_no, s.heading_bn, s.heading_en,
                   left(case a.authoritative_lang when 'bn' then coalesce(s.text_bn, '')
                                                  else coalesce(s.text_en, '') end, 300) as snippet,
                   s.source_url
              from law_section s
              join law_act a on a.id = s.act_id
             where s.id in (:ids)
            """)
        .param("ids", ids)
        .query(
            (rs, row) ->
                out.put(
                    rs.getLong("id"),
                    new SectionRow(
                        rs.getLong("id"),
                        rs.getString("section_key"),
                        rs.getString("code"),
                        rs.getString("section_no"),
                        rs.getString("heading_bn"),
                        rs.getString("heading_en"),
                        rs.getString("snippet"),
                        rs.getString("source_url"))))
        .list();
    return out;
  }

  /**
   * Edges of kind {@code USES_DEFINITION} or {@code REFERS_TO} from the seeds to current sections
   * that are not themselves seeds. Ordering and the cap belong to {@link GraphExpander}.
   */
  public List<GraphEdge> edgesFrom(Collection<Long> seedIds) {
    if (seedIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select distinct e.from_section_id, e.to_section_id, e.kind
              from section_edge e
              join law_section s on s.id = e.to_section_id and s.is_current
             where e.from_section_id in (:seeds)
               and e.kind in ('USES_DEFINITION', 'REFERS_TO')
               and e.to_section_id not in (:seeds)
            """)
        .param("seeds", seedIds)
        .query(
            (rs, row) ->
                new GraphEdge(
                    rs.getLong("from_section_id"),
                    rs.getLong("to_section_id"),
                    rs.getString("kind")))
        .list();
  }

  /**
   * IDs of the current sections for {@code actCode:sectionNo} pairs. A topic-map key whose version
   * label is outdated still resolves to the current version of that section.
   */
  public Map<String, Long> currentIdsByActAndNumber(Collection<String> actAndNumber) {
    if (actAndNumber.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> out = new LinkedHashMap<>();
    jdbc.sql(
            """
            select s.id, a.code || ':' || s.section_no as ref
              from law_section s
              join law_act a on a.id = s.act_id
             where s.is_current and a.code || ':' || s.section_no in (:refs)
            """)
        .param("refs", actAndNumber)
        .query((rs, row) -> out.put(rs.getString("ref"), rs.getLong("id")))
        .list();
    return out;
  }

  /** pgvector text form {@code [0.1,0.2,...]}. */
  public static String literal(float[] vector) {
    StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(vector[i]);
    }
    return sb.append(']').toString();
  }
}
