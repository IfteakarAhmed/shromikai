package bd.shromikai.analysis;

import bd.shromikai.analysis.CandidateSet.Candidate;
import bd.shromikai.law.Section;
import bd.shromikai.retrieval.RetrievalOrigin;
import bd.shromikai.retrieval.RetrievalResult;
import bd.shromikai.shared.ai.EgressContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/**
 * Step {@code RETRIEVE} (architecture §8.2, §8.3): per clause, hybrid search with query rewriting,
 * graph expansion and the topic map; the union of all clause candidates, topic-map sections first
 * and then by best fused score, capped at 40. Boilerplate clauses (general provisions, other) get
 * no search: no section is mapped to them and each search costs a rewriting call.
 */
@Component
public class CandidateRetriever {

  static final int ANALYSIS_MAX_SECTIONS = 40;
  static final int TEXT_CAP = 4000;
  private static final Set<ClauseType> SKIPPED =
      EnumSet.of(ClauseType.GENERAL_PROVISIONS, ClauseType.OTHER);

  private final CandidateSource retrieval;
  private final SectionSource law;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Semaphore slots = new Semaphore(8);

  public CandidateRetriever(CandidateSource retrieval, SectionSource law) {
    this.retrieval = retrieval;
    this.law = law;
  }

  /** What retrieval produced; {@code keywordOnly} when the embedding model was unavailable. */
  public record Result(CandidateSet candidates, boolean keywordOnly) {}

  public Result retrieve(List<Clause> clauses, EgressContext egress) {
    Map<String, Future<bd.shromikai.retrieval.Retrieval>> futures = new HashMap<>();
    for (Clause clause : clauses) {
      if (clause.type() == null || SKIPPED.contains(clause.type())) {
        continue;
      }
      futures.put(
          clause.id(),
          executor.submit(
              () -> {
                slots.acquire();
                try {
                  return retrieval.search(clause.text().reveal(), clause.type().name(), egress);
                } finally {
                  slots.release();
                }
              }));
    }
    Map<String, List<RetrievalResult>> perClause = new HashMap<>();
    boolean keywordOnly = false;
    try {
      for (Map.Entry<String, Future<bd.shromikai.retrieval.Retrieval>> entry : futures.entrySet()) {
        bd.shromikai.retrieval.Retrieval r = entry.getValue().get();
        keywordOnly |= r.keywordOnly();
        perClause.put(entry.getKey(), r.results());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      futures.values().forEach(f -> f.cancel(true));
      throw new IllegalStateException("retrieval interrupted", e);
    } catch (ExecutionException e) {
      futures.values().forEach(f -> f.cancel(true));
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("retrieval failed", e.getCause());
    }
    return new Result(build(clauses, perClause), keywordOnly);
  }

  /** Adds the sections of a corrective search to an existing set (still capped at 40). */
  public List<String> extend(CandidateSet set, List<RetrievalResult> results) {
    List<String> added = new ArrayList<>();
    for (RetrievalResult result : results) {
      if (set.contains(result.sectionKey())) {
        continue;
      }
      candidate(result.sectionKey()).filter(set::add).ifPresent(c -> added.add(c.sectionKey()));
    }
    return added;
  }

  private CandidateSet build(List<Clause> clauses, Map<String, List<RetrievalResult>> perClause) {
    // Topic-map sections first, then by best fused score.
    Map<String, double[]> best = new HashMap<>();
    Map<String, Boolean> topic = new HashMap<>();
    for (List<RetrievalResult> results : perClause.values()) {
      for (RetrievalResult r : results) {
        best.merge(
            r.sectionKey(),
            new double[] {r.fusedScore()},
            (a, b) -> new double[] {Math.max(a[0], b[0])});
        if (r.origin() == RetrievalOrigin.TOPIC_MAP) {
          topic.put(r.sectionKey(), true);
        }
      }
    }
    List<String> ordered = new ArrayList<>(best.keySet());
    ordered.sort(
        Comparator.<String, Boolean>comparing(k -> !topic.containsKey(k))
            .thenComparing(k -> -best.get(k)[0])
            .thenComparing(Comparator.naturalOrder()));
    CandidateSet set = new CandidateSet(ANALYSIS_MAX_SECTIONS);
    for (String key : ordered) {
      candidate(key).ifPresent(set::add);
    }
    for (Clause clause : clauses) {
      List<String> keys =
          perClause.getOrDefault(clause.id(), List.of()).stream()
              .map(RetrievalResult::sectionKey)
              .filter(set::contains)
              .distinct()
              .toList();
      set.assign(clause.id(), keys);
    }
    return set;
  }

  private java.util.Optional<Candidate> candidate(String sectionKey) {
    return law.current(sectionKey).map(CandidateRetriever::toCandidate);
  }

  static Candidate toCandidate(Section s) {
    String text = s.authoritativeText();
    boolean excerpt = text.length() > TEXT_CAP;
    return new Candidate(
        s.sectionKey(),
        s.actCode(),
        s.actTitleEn(),
        s.sectionNo(),
        s.headingEn() != null ? s.headingEn() : s.headingBn(),
        s.authoritativeLang(),
        excerpt ? text.substring(0, TEXT_CAP) : text,
        s.translation() == null ? null : s.translation().text(),
        excerpt);
  }
}
