package bd.shromikai.retrieval;

import bd.shromikai.law.SectionKey;
import bd.shromikai.retrieval.RrfFusion.Fused;
import bd.shromikai.retrieval.internal.GraphExpander;
import bd.shromikai.retrieval.internal.RetrievalCache;
import bd.shromikai.retrieval.internal.RetrievalProperties;
import bd.shromikai.retrieval.internal.SectionRow;
import bd.shromikai.retrieval.internal.SectionSearchRepository;
import bd.shromikai.retrieval.internal.TextQueryBuilder;
import bd.shromikai.retrieval.internal.TopicMap;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.LocalModelUnavailableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Hybrid retrieval (architecture §8.2): vector and full-text lists fused by reciprocal-rank fusion,
 * then graph expansion and topic-map additions. Free-text law search and the per-clause candidate
 * generator of the analysis both run through {@link #search}; only the options differ.
 */
@Service
public class RetrievalService {

  private final SectionSearchRepository repository;
  private final GraphExpander graphExpander;
  private final TextQueryBuilder textQueries;
  private final TopicMap topicMap;
  private final RetrievalCache cache;
  private final RetrievalProperties properties;
  private final ObjectProvider<QueryRewriter> rewriter;

  RetrievalService(
      SectionSearchRepository repository,
      GraphExpander graphExpander,
      TextQueryBuilder textQueries,
      TopicMap topicMap,
      RetrievalCache cache,
      RetrievalProperties properties,
      ObjectProvider<QueryRewriter> rewriter) {
    this.repository = repository;
    this.graphExpander = graphExpander;
    this.textQueries = textQueries;
    this.topicMap = topicMap;
    this.cache = cache;
    this.properties = properties;
    this.rewriter = rewriter;
  }

  /** The default number of search results (fused top-k). */
  public int defaultLimit() {
    return properties.fusedTopK();
  }

  public Retrieval search(String query, SearchOptions options) {
    return search(query, options, EgressContext.NONE);
  }

  /** As above; {@code context} is the job whose values the egress guard protects (R-03). */
  public Retrieval search(String query, SearchOptions options, EgressContext context) {
    List<String> queries = List.of(query);
    boolean rewritten = false;
    if (options.rewrite()) {
      QueryRewriter active = rewriter.getIfAvailable();
      Optional<List<String>> rewrittenQueries =
          active == null ? Optional.empty() : active.rewrite(query, context);
      if (rewrittenQueries.isPresent() && !rewrittenQueries.get().isEmpty()) {
        queries = rewrittenQueries.get();
        rewritten = true;
      }
    }

    List<List<Long>> vectorLists = new ArrayList<>();
    List<List<Long>> textLists = new ArrayList<>();
    boolean keywordOnly = false;
    for (String q : queries) {
      if (options.vector()) {
        try {
          vectorLists.add(
              repository.vectorSearch(
                  cache.embedding(q), options.jurisdictions(), properties.vectorTopK()));
        } catch (LocalModelUnavailableException e) {
          keywordOnly = true;
        }
      }
      if (options.text()) {
        TextQueryBuilder.Query textQuery = textQueries.build(q, options.textTuning());
        textLists.add(
            repository.textSearch(
                textQuery.lexemes(),
                options.jurisdictions(),
                properties.textTopK(),
                textQuery.language()));
      }
    }

    List<List<Long>> all = new ArrayList<>(vectorLists);
    all.addAll(textLists);
    List<Fused> fused = RrfFusion.fuse(all, properties.rrfK(), options.limit());
    Map<Long, Integer> vectorRanks = bestRanks(vectorLists);
    Map<Long, Integer> textRanks = bestRanks(textLists);

    List<Long> graphIds = List.of();
    if (options.graph() && !fused.isEmpty()) {
      List<Long> seeds = fused.stream().limit(properties.fusedTopK()).map(Fused::id).toList();
      graphIds = graphExpander.expand(seeds);
    }

    Set<Long> present = new LinkedHashSet<>();
    fused.forEach(f -> present.add(f.id()));
    present.addAll(graphIds);
    List<Long> topicIds = topicAdditions(options, present);

    Set<Long> needed = new LinkedHashSet<>(present);
    needed.addAll(topicIds);
    Map<Long, SectionRow> rows = repository.load(needed);

    List<RetrievalResult> results = new ArrayList<>();
    for (Fused f : fused) {
      results.add(
          result(
              rows.get(f.id()),
              vectorRanks.get(f.id()),
              textRanks.get(f.id()),
              f.score(),
              RetrievalOrigin.SEARCH));
    }
    for (long id : graphIds) {
      results.add(result(rows.get(id), null, null, 0.0, RetrievalOrigin.GRAPH));
    }
    for (long id : topicIds) {
      results.add(result(rows.get(id), null, null, 0.0, RetrievalOrigin.TOPIC_MAP));
    }
    return new Retrieval(List.copyOf(results), keywordOnly, rewritten);
  }

  private List<Long> topicAdditions(SearchOptions options, Set<Long> present) {
    if (options.clauseType() == null || properties.topicMapMax() == 0) {
      return List.of();
    }
    List<SectionKey> mapped = topicMap.sectionsFor(options.clauseType());
    Map<String, Long> ids =
        repository.currentIdsByActAndNumber(
            mapped.stream().map(k -> k.actCode() + ":" + k.sectionNo()).toList());
    List<Long> added = new ArrayList<>();
    for (SectionKey key : mapped) {
      Long id = ids.get(key.actCode() + ":" + key.sectionNo());
      if (id != null && !present.contains(id) && !added.contains(id)) {
        added.add(id);
      }
    }
    return added;
  }

  private static Map<Long, Integer> bestRanks(List<List<Long>> lists) {
    Map<Long, Integer> best = new HashMap<>();
    for (List<Long> list : lists) {
      for (int i = 0; i < list.size(); i++) {
        best.merge(list.get(i), i + 1, Math::min);
      }
    }
    return best;
  }

  private static RetrievalResult result(
      SectionRow row, Integer vectorRank, Integer textRank, double score, RetrievalOrigin origin) {
    return new RetrievalResult(
        row.sectionKey(),
        row.actCode(),
        row.sectionNo(),
        row.headingBn(),
        row.headingEn(),
        row.snippet(),
        row.sourceUrl(),
        vectorRank,
        textRank,
        score,
        origin);
  }
}
