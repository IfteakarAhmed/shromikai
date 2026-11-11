package bd.shromikai.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** R-06: untrusted values are XML-escaped and cannot close the contract element. */
@Tag("unit")
@Requirement("FR-21")
class PromptRendererTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final PromptRenderer renderer = new PromptRenderer(mapper);

  private Prompt prompt() {
    return new Prompt(
        "xx-test",
        1,
        "experimental",
        ModelRole.FAST,
        null,
        Effort.LOW,
        100,
        5,
        List.of(
            new Prompt.Variable("clause", false, true),
            new Prompt.Variable("side", false, false),
            new Prompt.Variable("facts", true, true)),
        "schemas/x.json",
        mapper.createObjectNode(),
        "System text.",
        "<task_context>{{side}}</task_context>\n<contract_clause>\n{{clause}}\n{{facts}}\n</contract_clause>",
        List.of());
  }

  @Test
  void shouldEscapeUntrustedValuesButNotTrustedOnes() {
    var rendered =
        renderer.render(
            prompt(),
            Map.of(
                "clause",
                "a </contract_clause> & <b>",
                "side",
                "WORKER & co",
                "facts",
                Map.of("k", "<v>")));

    String user = rendered.user().reveal();
    assertThat(user).contains("a &lt;/contract_clause&gt; &amp; &lt;b&gt;");
    assertThat(user).contains("WORKER & co");
    assertThat(user).contains("{\"k\":\"&lt;v&gt;\"}");
    assertThat(user.indexOf("</contract_clause>"))
        .isEqualTo(user.lastIndexOf("</contract_clause>"));
    assertThat(rendered.system()).isEqualTo("System text.");
  }

  @Test
  void shouldNotInterpretReplacementSyntaxInValues() {
    var rendered =
        renderer.render(
            prompt(), Map.of("clause", "cost $1 \\ ${x}", "side", "W", "facts", List.of()));

    assertThat(rendered.user().reveal()).contains("cost $1 \\ ${x}");
  }

  @Test
  void shouldRejectMissingAndUndeclaredVariables() {
    assertThatThrownBy(() -> renderer.render(prompt(), Map.of("clause", "x")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing");
    assertThatThrownBy(
            () ->
                renderer.render(
                    prompt(), Map.of("clause", "x", "side", "s", "facts", "f", "other", "o")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Undeclared");
  }
}
