package bd.shromikai.memo;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word-level diff between an original and a revised clause (FR-52, java-diff-utils). A token is a
 * run of letters, combining marks and digits (so a Bangla word with its conjuncts and vowel signs
 * stays whole) or one other non-space character, so punctuation is its own token. Each token keeps
 * the white space in front of it; tokens compare without it. Adjacent segments of the same
 * operation are merged. The texts here are redacted clause text; nothing is stored.
 */
public final class RedlineDiff {

  /** The operation of a segment. */
  public enum Op {
    EQUAL,
    INSERT,
    DELETE
  }

  /** A run of text with its operation; the white space in front of each word is kept. */
  public record Segment(Op op, String text) {}

  private static final Pattern TOKEN = Pattern.compile("(\\s*)([\\p{L}\\p{M}\\p{N}]+|[^\\s])");

  private RedlineDiff() {}

  public static List<Segment> words(String original, String proposed) {
    List<String> a = tokens(original);
    List<String> b = tokens(proposed);
    List<String> keysA = a.stream().map(String::strip).toList();
    List<String> keysB = b.stream().map(String::strip).toList();
    Patch<String> patch = DiffUtils.diff(keysA, keysB);
    List<Segment> out = new ArrayList<>();
    int posA = 0;
    int posB = 0;
    for (AbstractDelta<String> delta : patch.getDeltas()) {
      int startA = delta.getSource().getPosition();
      int startB = delta.getTarget().getPosition();
      while (posA < startA) {
        add(out, Op.EQUAL, b.get(posB));
        posA++;
        posB++;
      }
      for (int i = 0; i < delta.getSource().size(); i++) {
        add(out, Op.DELETE, a.get(startA + i));
      }
      for (int i = 0; i < delta.getTarget().size(); i++) {
        add(out, Op.INSERT, b.get(startB + i));
      }
      posA = startA + delta.getSource().size();
      posB = startB + delta.getTarget().size();
    }
    while (posA < a.size()) {
      add(out, Op.EQUAL, b.get(posB));
      posA++;
      posB++;
    }
    return List.copyOf(out);
  }

  private static List<String> tokens(String text) {
    List<String> out = new ArrayList<>();
    Matcher m = TOKEN.matcher(text);
    while (m.find()) {
      out.add(m.group());
    }
    return out;
  }

  private static void add(List<Segment> out, Op op, String text) {
    if (!out.isEmpty() && out.get(out.size() - 1).op() == op) {
      Segment last = out.remove(out.size() - 1);
      out.add(new Segment(op, last.text() + text));
    } else {
      out.add(new Segment(op, text));
    }
  }
}
