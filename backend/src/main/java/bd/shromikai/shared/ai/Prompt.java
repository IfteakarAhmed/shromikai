package bd.shromikai.shared.ai;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A runtime prompt loaded from {@code prompts/{id}/v{n}.md} (AGENTS.md §5).
 *
 * @param temperature {@code null} for {@code provider-default} (Claude roles, ADR-0005)
 * @param effort Claude effort, {@code null} for other providers
 * @param outputSchema the parsed JSON Schema 2020-12 of the output
 * @param system trusted text of the {@code <system>} element
 * @param user template of the {@code <user>} element with {@code {{variable}}} placeholders
 */
public record Prompt(
    String id,
    int version,
    String status,
    ModelRole role,
    @Nullable Double temperature,
    @Nullable Effort effort,
    int maxOutputTokens,
    int timeoutSeconds,
    List<Variable> variables,
    String outputSchemaLocation,
    com.fasterxml.jackson.databind.JsonNode outputSchema,
    String system,
    String user) {

  /** A declared input variable; {@code json} values are serialised, untrusted ones escaped. */
  public record Variable(String name, boolean json, boolean untrusted) {}
}
