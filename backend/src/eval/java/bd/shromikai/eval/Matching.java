package bd.shromikai.eval;

import bd.shromikai.eval.EvalResults.PredictedFinding;
import bd.shromikai.eval.Labels.GoldRisk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Matching predicted findings to gold risks (evaluation.md §7): location, topic, side and, when the
 * gold risk requires one, a VERIFIED citation whose key is acceptable. One-to-one, greedy in
 * descending confidence. The only change from §7: clauses are compared by printed label (the labels
 * file records the printed label of each clause), because the job keeps clause labels.
 */
public final class Matching {

  private Matching() {}

  /** "৪." → "৪", " 2) " → "2", "ধারা ৫" → "৫": the trailing number of a printed label. */
  public static String normaliseLabel(@Nullable String label) {
    if (label == null) {
      return "";
    }
    String s = label.strip();
    java.util.regex.Matcher m = LAST_NUMBER.matcher(s);
    return m.find() ? m.group(1) : s.replaceAll("[.)\\s]+$", "");
  }

  /** The last number of a label, with an optional letter suffix such as "3A". */
  private static final java.util.regex.Pattern LAST_NUMBER =
      java.util.regex.Pattern.compile("(\\p{Nd}+[A-Za-z]?)[^\\p{Nd}]*$");

  /** Gold risk ID → matched predicted finding ID. */
  public static Map<String, String> match(
      List<PredictedFinding> predicted, List<GoldRisk> gold, String side) {
    List<PredictedFinding> ordered = new ArrayList<>(predicted);
    ordered.sort(Comparator.comparingDouble(PredictedFinding::confidence).reversed());
    Map<String, String> out = new LinkedHashMap<>();
    Set<String> takenGold = new HashSet<>();
    for (PredictedFinding p : ordered) {
      for (GoldRisk g : gold) {
        if (takenGold.contains(g.id()) || !matches(p, g, side)) {
          continue;
        }
        out.put(g.id(), p.id());
        takenGold.add(g.id());
        break;
      }
    }
    return out;
  }

  static boolean matches(PredictedFinding p, GoldRisk g, String side) {
    Set<String> pl = new HashSet<>();
    p.clauseLabels().forEach(l -> pl.add(normaliseLabel(l)));
    Set<String> gl = new HashSet<>();
    g.clauseLabels().forEach(l -> gl.add(normaliseLabel(l)));
    boolean location;
    if (gl.isEmpty()) {
      location = pl.isEmpty() && p.clauseType().equals(g.clauseType());
    } else {
      Set<String> both = new HashSet<>(pl);
      both.retainAll(gl);
      location = !both.isEmpty();
    }
    if (!location || !p.clauseType().equals(g.clauseType())) {
      return false;
    }
    if (!g.side().equals("BOTH") && !g.side().equals(side)) {
      return false;
    }
    if (g.requiresCitation()) {
      return "VERIFIED".equals(p.verification())
          && p.verifiedSectionKeys().stream().anyMatch(g.acceptableSectionKeys()::contains);
    }
    return true;
  }
}
