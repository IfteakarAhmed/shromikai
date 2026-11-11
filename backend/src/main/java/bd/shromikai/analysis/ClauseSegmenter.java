package bd.shromikai.analysis;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.ModelException;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.text.BanglaNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Splits the redacted paragraphs into clauses (FR-33). The rule-based splitter handles the
 * numbering styles {@code ১.}/{@code 1.}, {@code (ক)}/{@code (a)} and {@code i.}: the style that
 * ranks highest in the document (digits, then parenthesised letters, then roman numerals) starts a
 * clause, and lower styles stay inside their clause. Text before the first clause is one opening
 * clause. Without at least two numbered clauses the {@code clause-segment} prompt (Haiku) groups
 * the paragraphs; an inconsistent answer falls back to one clause per paragraph.
 */
@Component
public final class ClauseSegmenter {

  private enum Style {
    DIGIT,
    PAREN,
    ROMAN
  }

  private static final Pattern DIGIT = Pattern.compile("^\\s*(\\d{1,3}[.)])\\s");
  private static final Pattern PAREN =
      Pattern.compile("^\\s*(\\((?:[a-zA-Z]|\\d{1,2}|[ক-হ]{1,2}|[০-৯]{1,2})\\))\\s");
  private static final Pattern BANGLA_LETTER = Pattern.compile("^\\s*([ক-হ][.)])\\s");
  private static final Pattern ROMAN = Pattern.compile("^\\s*([ivxIVX]{1,4}\\.)\\s");

  private final ModelGateway gateway;

  public ClauseSegmenter(ModelGateway gateway) {
    this.gateway = gateway;
  }

  /** Rule-based split, falling back to the model for unnumbered text. */
  public List<Clause> split(List<Para> paragraphs, @Nullable EgressContext context) {
    Optional<List<Clause>> numbered = byNumbering(paragraphs);
    if (numbered.isPresent()) {
      return numbered.get();
    }
    return byModel(paragraphs, context);
  }

  /** The rule-based split alone; empty if the text has fewer than two numbered clauses. */
  public static Optional<List<Clause>> byNumbering(List<Para> paragraphs) {
    Style[] styles = new Style[paragraphs.size()];
    String[] labels = new String[paragraphs.size()];
    Style top = null;
    for (int i = 0; i < paragraphs.size(); i++) {
      String text = BanglaNormalizer.toLatinDigits(paragraphs.get(i).text().reveal());
      String original = paragraphs.get(i).text().reveal();
      Style style = null;
      String label = null;
      Matcher m;
      if ((m = DIGIT.matcher(text)).find()) {
        style = Style.DIGIT;
        label = original.substring(m.start(1), m.end(1));
      } else if ((m = PAREN.matcher(original)).find()
          || (m = BANGLA_LETTER.matcher(original)).find()) {
        style = Style.PAREN;
        label = original.substring(m.start(1), m.end(1));
      } else if ((m = ROMAN.matcher(original)).find()) {
        style = Style.ROMAN;
        label = original.substring(m.start(1), m.end(1));
      }
      styles[i] = style;
      labels[i] = label;
      if (style != null && (top == null || style.ordinal() < top.ordinal())) {
        top = style;
      }
    }
    if (top == null) {
      return Optional.empty();
    }
    int starts = 0;
    for (Style style : styles) {
      if (style == top) {
        starts++;
      }
    }
    if (starts < 2) {
      return Optional.empty();
    }
    List<List<Para>> groups = new ArrayList<>();
    List<@Nullable String> groupLabels = new ArrayList<>();
    List<Para> current = new ArrayList<>();
    String currentLabel = null;
    for (int i = 0; i < paragraphs.size(); i++) {
      if (styles[i] == top) {
        if (!current.isEmpty()) {
          groups.add(current);
          groupLabels.add(currentLabel);
        }
        current = new ArrayList<>();
        currentLabel = labels[i];
      }
      current.add(paragraphs.get(i));
    }
    groups.add(current);
    groupLabels.add(currentLabel);
    return Optional.of(toClauses(groups, groupLabels));
  }

  private List<Clause> byModel(List<Para> paragraphs, @Nullable EgressContext context) {
    List<Map<String, String>> input = new ArrayList<>();
    for (Para para : paragraphs) {
      input.add(Map.of("id", para.id(), "text", para.text().reveal()));
    }
    try {
      JsonNode out = gateway.call(PROMPT_SEGMENT, Map.of("paragraphs", input), context).output();
      Optional<List<List<Para>>> groups = validGroups(out, paragraphs);
      if (groups.isPresent()) {
        List<@Nullable String> labels = new ArrayList<>();
        groups.get().forEach(g -> labels.add(null));
        return toClauses(groups.get(), labels);
      }
    } catch (ModelException e) {
      if (e instanceof bd.shromikai.shared.ai.EgressBlockedException
          || e instanceof bd.shromikai.shared.ai.LocalModelUnavailableException) {
        throw e;
      }
      // segmentation alone never fails an analysis: fall through to one clause per paragraph
    }
    List<List<Para>> single = new ArrayList<>();
    List<@Nullable String> labels = new ArrayList<>();
    for (Para para : paragraphs) {
      single.add(List.of(para));
      labels.add(null);
    }
    return toClauses(single, labels);
  }

  static final String PROMPT_SEGMENT = "clause-segment";

  private static Optional<List<List<Para>>> validGroups(JsonNode out, List<Para> paragraphs) {
    if (!"OK".equals(out.path("status").asText())) {
      return Optional.empty();
    }
    Map<String, Para> byId = new LinkedHashMap<>();
    paragraphs.forEach(p -> byId.put(p.id(), p));
    List<String> order = new ArrayList<>(byId.keySet());
    Map<String, Integer> used = new HashMap<>();
    List<List<Para>> groups = new ArrayList<>();
    int expected = 0;
    for (JsonNode clause : out.path("clauses")) {
      List<Para> group = new ArrayList<>();
      for (JsonNode id : clause.path("paragraph_ids")) {
        String key = id.asText();
        if (!byId.containsKey(key) || used.merge(key, 1, Integer::sum) > 1) {
          return Optional.empty();
        }
        if (!order.get(expected).equals(key)) {
          return Optional.empty(); // out of document order
        }
        expected++;
        group.add(byId.get(key));
      }
      if (group.isEmpty()) {
        return Optional.empty();
      }
      groups.add(group);
    }
    return expected == paragraphs.size() ? Optional.of(groups) : Optional.empty();
  }

  private static List<Clause> toClauses(List<List<Para>> groups, List<@Nullable String> labels) {
    List<Clause> out = new ArrayList<>();
    for (int i = 0; i < groups.size(); i++) {
      List<Para> group = groups.get(i);
      StringBuilder text = new StringBuilder();
      Set<ClauseFlag> flags = EnumSet.noneOf(ClauseFlag.class);
      List<String> ids = new ArrayList<>();
      for (Para para : group) {
        if (!text.isEmpty()) {
          text.append('\n');
        }
        text.append(para.text().reveal());
        flags.addAll(para.flags());
        ids.add(para.id());
      }
      out.add(
          new Clause(
              "c" + (i + 1),
              labels.get(i),
              ids,
              SensitiveText.of(text.toString()),
              flags,
              null,
              0));
    }
    return out;
  }
}
