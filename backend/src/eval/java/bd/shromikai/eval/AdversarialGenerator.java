package bd.shromikai.eval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Builds the adversarial citation set from the ingested corpus (evaluation.md §4.4) for a seed.
 * Classes that need no judgement are generated: sections that do not exist, non-current versions,
 * quotes with 15 % of their letters replaced. The claim-based classes use claims drafted by a
 * person or an AI from the section text ({@link ClaimDraft}); an unrelated claim is the claim
 * written for a section of another topic. Every case starts unreviewed.
 */
public final class AdversarialGenerator {

  /** A section of the corpus: its key, act, number, currency and authoritative text. */
  public record SectionRow(
      String sectionKey,
      String actCode,
      String sectionNo,
      boolean current,
      String text,
      String lang) {}

  /**
   * A drafted claim for one section: a quote copied from its authoritative text, a claim the quote
   * supports, optionally a claim it contradicts, and a topic to pair unrelated claims.
   */
  public record ClaimDraft(
      String sectionKey,
      String quote,
      String validClaim,
      @Nullable String contradictedClaim,
      String topic) {}

  static final int NONEXISTENT = 15;
  static final int NOT_CURRENT = 10;
  static final int MISQUOTE = 15;
  static final int UNRELATED = 10;
  static final int CONTRADICTED = 10;
  static final int VALID = 20;

  private static final String GENERIC_CLAIM =
      "This section sets the rule that the clause under review must follow.";

  private AdversarialGenerator() {}

  /** The first sentence-sized piece of a section text (at most 160 characters, word-aligned). */
  public static String sourceQuote(String text) {
    String t = text.strip().replaceAll("\\s+", " ");
    if (t.length() <= 160) {
      return t;
    }
    int cut = t.lastIndexOf(' ', 160);
    return t.substring(0, cut > 80 ? cut : 160);
  }

  public static List<AdversarialCase> generate(
      List<SectionRow> corpus, List<ClaimDraft> drafts, long seed) {
    Random random = new Random(seed);
    List<SectionRow> current = corpus.stream().filter(SectionRow::current).toList();
    Set<String> keys = new HashSet<>();
    corpus.forEach(r -> keys.add(r.sectionKey()));
    List<AdversarialCase> out = new ArrayList<>();

    // 1. Sections that do not exist: numbers above the highest of each act, and unknown versions.
    TreeSet<String> acts = new TreeSet<>();
    current.forEach(r -> acts.add(r.actCode()));
    List<String> fakeKeys = new ArrayList<>();
    for (String act : acts) {
      int max =
          current.stream()
              .filter(r -> r.actCode().equals(act))
              .mapToInt(r -> leadingNumber(r.sectionNo()))
              .max()
              .orElse(0);
      for (int k = 1; k <= 5; k++) {
        fakeKeys.add(act + ":" + (max + k * 7) + "@original");
      }
      fakeKeys.add(act + ":" + (1 + random.nextInt(Math.max(1, max))) + "@amended-2099");
      fakeKeys.add(act + ":" + (1 + random.nextInt(Math.max(1, max))) + "Z@original");
    }
    int made = 0;
    for (String key : fakeKeys) {
      if (made == NONEXISTENT || keys.contains(key)) {
        continue;
      }
      SectionRow donor = current.get(random.nextInt(current.size()));
      out.add(
          new AdversarialCase(
              id(out),
              "NONEXISTENT_SECTION",
              key,
              sourceQuote(donor.text()),
              GENERIC_CLAIM,
              "NOT_VERIFIED",
              "key not in the corpus; quote copied from " + donor.sectionKey(),
              null));
      made++;
    }

    // 2. Versions that are not current.
    corpus.stream()
        .filter(r -> !r.current())
        .limit(NOT_CURRENT)
        .forEach(
            r ->
                out.add(
                    new AdversarialCase(
                        id(out),
                        "NOT_CURRENT",
                        r.sectionKey(),
                        sourceQuote(r.text()),
                        GENERIC_CLAIM,
                        "NOT_VERIFIED",
                        "non-current version; quote copied from that version's own text",
                        null)));

    // 3. Misquotes: 15 % of the letters of a real quote replaced.
    List<SectionRow> pool = new ArrayList<>(current);
    java.util.Collections.shuffle(pool, random);
    int misquotes = 0;
    for (SectionRow r : pool) {
      if (misquotes == MISQUOTE) {
        break;
      }
      String source = sourceQuote(r.text());
      if (source.codePoints().filter(Character::isLetter).count() < 20) {
        continue;
      }
      String bad = mutate(source, random);
      if (r.text().replaceAll("\\s+", " ").contains(bad)) {
        continue;
      }
      out.add(
          new AdversarialCase(
              id(out),
              "MISQUOTE",
              r.sectionKey(),
              bad,
              GENERIC_CLAIM,
              "NOT_VERIFIED",
              String.format(
                  Locale.ROOT, "valid section; 15 %% of quote letters replaced (seed %d)", seed),
              null));
      misquotes++;
    }

    // 4. Unrelated claims: a section's quote with a claim drafted for a section of another topic.
    List<ClaimDraft> usable = drafts.stream().filter(d -> sectionHasQuote(corpus, d)).toList();
    int unrelated = 0;
    for (int i = 0; i < usable.size() && unrelated < UNRELATED; i++) {
      ClaimDraft d = usable.get(i);
      List<ClaimDraft> others = usable.stream().filter(o -> !o.topic().equals(d.topic())).toList();
      if (others.isEmpty()) {
        continue;
      }
      ClaimDraft other = others.get(random.nextInt(others.size()));
      out.add(
          new AdversarialCase(
              id(out),
              "UNRELATED_CLAIM",
              d.sectionKey(),
              d.quote(),
              other.validClaim(),
              "NOT_VERIFIED",
              "quote of " + d.sectionKey() + " with the claim written for " + other.sectionKey(),
              null));
      unrelated++;
    }

    // 5. Contradicted claims.
    usable.stream()
        .filter(d -> d.contradictedClaim() != null)
        .limit(CONTRADICTED)
        .forEach(
            d ->
                out.add(
                    new AdversarialCase(
                        id(out),
                        "CONTRADICTED_CLAIM",
                        d.sectionKey(),
                        d.quote(),
                        d.contradictedClaim(),
                        "NOT_VERIFIED",
                        "claim drafted to contradict the quoted text",
                        null)));

    // 6. Valid controls.
    usable.stream()
        .limit(VALID)
        .forEach(
            d ->
                out.add(
                    new AdversarialCase(
                        id(out),
                        "VALID_CONTROL",
                        d.sectionKey(),
                        d.quote(),
                        d.validClaim(),
                        "VERIFIED",
                        "quote copied from the section; claim drafted from the quoted text",
                        null)));
    return List.copyOf(out);
  }

  private static boolean sectionHasQuote(List<SectionRow> corpus, ClaimDraft d) {
    return corpus.stream()
        .anyMatch(
            r ->
                r.current()
                    && r.sectionKey().equals(d.sectionKey())
                    && r.text()
                        .replaceAll("\\s+", " ")
                        .contains(d.quote().replaceAll("\\s+", " ")));
  }

  private static String id(List<AdversarialCase> sofar) {
    return String.format(Locale.ROOT, "ac-%03d", sofar.size() + 1);
  }

  private static int leadingNumber(String sectionNo) {
    String digits = sectionNo.replaceAll("^(\\d+).*$", "$1");
    try {
      return Integer.parseInt(digits);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** Replaces ceil(15 %) of the letters with another letter of the same script. */
  static String mutate(String source, Random random) {
    int[] cps = source.codePoints().toArray();
    List<Integer> letters = new ArrayList<>();
    for (int i = 0; i < cps.length; i++) {
      if (Character.isLetter(cps[i])) {
        letters.add(i);
      }
    }
    int target = (int) Math.ceil(0.15 * letters.size());
    java.util.Collections.shuffle(letters, random);
    for (int k = 0; k < target; k++) {
      int i = letters.get(k);
      int cp = cps[i];
      boolean bangla = cp >= 0x0980 && cp <= 0x09FF;
      int replacement;
      do {
        replacement =
            bangla ? 0x0995 + random.nextInt(0x09A8 - 0x0995 + 1) : 'a' + random.nextInt(26);
      } while (replacement == cp);
      cps[i] = replacement;
    }
    return new String(cps, 0, cps.length);
  }
}
