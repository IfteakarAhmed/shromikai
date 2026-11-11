package bd.shromikai.shared.ai;

import bd.shromikai.shared.SensitiveText;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Fills a prompt's {@code {{variable}}} placeholders. Values of untrusted variables are XML-escaped
 * ({@code &}, {@code <}, {@code >}) so a document cannot close the {@code <contract_clause>}
 * element (AGENTS.md §5.3 rule 3, R-06). The system turn has no variables.
 */
@Component
public final class PromptRenderer {

  private static final Pattern VARIABLE =
      Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*}}");

  /** The rendered turns; the user turn may hold contract-derived text. */
  public record Rendered(String system, SensitiveText user) {}

  private final ObjectMapper mapper;

  public PromptRenderer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public Rendered render(Prompt prompt, Map<String, ?> values) {
    Map<String, Prompt.Variable> declared = new java.util.HashMap<>();
    prompt.variables().forEach(v -> declared.put(v.name(), v));
    for (String name : values.keySet()) {
      if (!declared.containsKey(name)) {
        throw new IllegalArgumentException("Undeclared variable " + name);
      }
    }
    Matcher matcher = VARIABLE.matcher(prompt.user());
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      if (!values.containsKey(name)) {
        throw new IllegalArgumentException("Missing variable " + name);
      }
      Prompt.Variable variable = declared.get(name);
      String text = text(variable, values.get(name));
      matcher.appendReplacement(
          out, Matcher.quoteReplacement(variable.untrusted() ? escape(text) : text));
    }
    matcher.appendTail(out);
    return new Rendered(prompt.system(), SensitiveText.of(out.toString()));
  }

  /** Escapes validator messages echoed back to the model (they may quote model output). */
  static String escapeForRepair(String text) {
    return escape(text);
  }

  static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private String text(Prompt.Variable variable, Object value) {
    if (!variable.json()) {
      return String.valueOf(value);
    }
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Variable " + variable.name() + " is not JSON", e);
    }
  }
}
