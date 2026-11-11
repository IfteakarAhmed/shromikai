package bd.shromikai.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The statute sections a risk agent may cite (architecture §8.3): the analysis-level set, capped at
 * 40, and each clause's own candidates. Texts longer than 4,000 characters are shortened and marked
 * {@code excerpt}; the verifier always uses the full stored text.
 */
public final class CandidateSet {

  /** One candidate as the model sees it. */
  public record Candidate(
      String sectionKey,
      String actCode,
      String actTitle,
      String sectionNo,
      @Nullable String heading,
      String authoritativeLang,
      String authoritativeText,
      @Nullable String translationText,
      boolean excerpt) {}

  private final Map<String, Candidate> sections = new LinkedHashMap<>();
  private final Map<String, List<String>> byClause = new LinkedHashMap<>();
  private final int cap;

  public CandidateSet(int cap) {
    this.cap = cap;
  }

  /** Adds a section if there is room; returns whether it is (now) in the set. */
  public synchronized boolean add(Candidate candidate) {
    if (sections.containsKey(candidate.sectionKey())) {
      return true;
    }
    if (sections.size() >= cap) {
      return false;
    }
    sections.put(candidate.sectionKey(), candidate);
    return true;
  }

  public synchronized void assign(String clauseId, List<String> keys) {
    byClause.put(clauseId, List.copyOf(keys));
  }

  public synchronized boolean contains(String sectionKey) {
    return sections.containsKey(sectionKey);
  }

  public synchronized List<Candidate> all() {
    return List.copyOf(sections.values());
  }

  public synchronized Set<String> keys() {
    return Set.copyOf(sections.keySet());
  }

  public synchronized List<String> keysOf(String clauseId) {
    return byClause.getOrDefault(clauseId, List.of());
  }

  /** The candidates of the clauses of a batch, restricted to the analysis-level set. */
  public synchronized List<Candidate> forClauses(List<String> clauseIds) {
    List<Candidate> out = new ArrayList<>();
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (String clauseId : clauseIds) {
      for (String key : byClause.getOrDefault(clauseId, List.of())) {
        Candidate candidate = sections.get(key);
        if (candidate != null && seen.add(key)) {
          out.add(candidate);
        }
      }
    }
    return out;
  }

  public int size() {
    return sections.size();
  }
}
