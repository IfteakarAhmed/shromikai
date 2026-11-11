package bd.shromikai.mcp.internal;

import bd.shromikai.auth.ApiKeyPrincipal;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Builds what the MCP server registers (FR-64): the five tools from {@link McpToolCatalog}, the
 * resource template {@code law://bd/{act_code}/{section_no}} and the prompt template {@code
 * review-employment-contract}. Every call, tool or not, passes {@link McpCallGuard}.
 */
@Component
@Profile("mcp")
public final class McpServerSpecifications {

  static final String RESOURCE_TEMPLATE = "law://bd/{act_code}/{section_no}";
  static final String PROMPT = "review-employment-contract";
  private static final Pattern RESOURCE = Pattern.compile("^law://bd/([^/]+)/([^/]+)$");

  private final McpToolCatalog catalog;
  private final McpCallGuard guard;
  private final LawTools laws;
  private final VerifyTool verify;
  private final AnalyzeTool analyze;
  private final McpJsonMapper json;

  McpServerSpecifications(
      McpToolCatalog catalog,
      McpCallGuard guard,
      LawTools laws,
      VerifyTool verify,
      AnalyzeTool analyze,
      McpJsonMapper json) {
    this.catalog = catalog;
    this.guard = guard;
    this.laws = laws;
    this.verify = verify;
    this.analyze = analyze;
    this.json = json;
  }

  public List<McpServerFeatures.SyncToolSpecification> tools() {
    List<McpServerFeatures.SyncToolSpecification> out = new ArrayList<>();
    for (McpToolCatalog.Definition def : catalog.definitions()) {
      McpSchema.Tool tool =
          McpSchema.Tool.builder()
              .name(def.name())
              .title(def.title())
              .description(def.description())
              .inputSchema(json, def.input().toString())
              .outputSchema(json, def.output().toString())
              .annotations(
                  new McpSchema.ToolAnnotations(def.title(), true, false, true, false, false))
              .build();
      out.add(
          McpServerFeatures.SyncToolSpecification.builder()
              .tool(tool)
              .callHandler(
                  (exchange, request) ->
                      guard.tool(
                          exchange,
                          key -> dispatch(def.name(), new Args(request.arguments()), key)))
              .build());
    }
    return out;
  }

  private McpCallGuard.Output dispatch(String name, Args args, ApiKeyPrincipal key) {
    return switch (name) {
      case "search_bd_law" -> laws.search(args);
      case "get_law_section" -> laws.section(args);
      case "verify_legal_citation" -> verify.verify(args);
      case "analyze_employment_contract" -> analyze.analyse(args, key.keyId(), key.orgId());
      case "list_jurisdiction_packs" -> laws.packs();
      default -> throw new ProblemException(ProblemType.NOT_FOUND);
    };
  }

  public List<McpServerFeatures.SyncResourceTemplateSpecification> resourceTemplates() {
    McpSchema.ResourceTemplate template =
        McpSchema.ResourceTemplate.builder()
            .uriTemplate(RESOURCE_TEMPLATE)
            .name("law-section")
            .title("Statute section")
            .description(
                "The current official text of one statute section as markdown, with version,"
                    + " effective date and source URL. Not legal advice.")
            .mimeType("text/markdown")
            .build();
    return List.of(
        new McpServerFeatures.SyncResourceTemplateSpecification(
            template,
            (exchange, request) -> {
              ApiKeyPrincipal key = guard.admit(exchange.transportContext());
              try {
                Matcher uri = RESOURCE.matcher(request.uri());
                if (!uri.matches()) {
                  throw new ProblemException(ProblemType.NOT_FOUND);
                }
                String markdown =
                    laws.markdown(
                        URLDecoder.decode(uri.group(1), StandardCharsets.UTF_8),
                        URLDecoder.decode(uri.group(2), StandardCharsets.UTF_8));
                return new McpSchema.ReadResourceResult(
                    List.of(
                        new McpSchema.TextResourceContents(
                            request.uri(), "text/markdown", markdown)));
              } finally {
                guard.meter(key, 0);
              }
            }));
  }

  public List<McpServerFeatures.SyncPromptSpecification> prompts() {
    McpSchema.Prompt prompt =
        new McpSchema.Prompt(
            PROMPT,
            "Review an employment contract",
            "Guides an agent to review one employment contract with ShromikAI: analyse it, present"
                + " only verified citations, verify any other citation first, and say uncertain"
                + " instead of guessing.",
            List.of(
                new McpSchema.PromptArgument(
                    "side", "Whose side to take: WORKER or EMPLOYER", true),
                new McpSchema.PromptArgument(
                    "language", "Language of the answer: bn (Bangla) or en (English)", false)));
    String template = read("mcp/prompts/review-employment-contract.md");
    return List.of(
        new McpServerFeatures.SyncPromptSpecification(
            prompt,
            (exchange, request) -> {
              ApiKeyPrincipal key = guard.admit(exchange.transportContext());
              try {
                String side =
                    argument(request.arguments(), "side", Set.of("WORKER", "EMPLOYER"), null);
                String language =
                    argument(request.arguments(), "language", Set.of("bn", "en"), "bn");
                String text = template.replace("{{side}}", side).replace("{{language}}", language);
                return new McpSchema.GetPromptResult(
                    prompt.description(),
                    List.of(
                        new McpSchema.PromptMessage(
                            McpSchema.Role.USER, new McpSchema.TextContent(text))));
              } finally {
                guard.meter(key, 0);
              }
            }));
  }

  private static String argument(
      java.util.Map<String, Object> arguments, String name, Set<String> allowed, String fallback) {
    Object value = arguments == null ? null : arguments.get(name);
    if (value == null && fallback != null) {
      return fallback;
    }
    if (!(value instanceof String text) || !allowed.contains(text)) {
      throw new ProblemException(
          ProblemType.VALIDATION_FAILED,
          name + " must be one of " + new java.util.TreeSet<>(allowed));
    }
    return text;
  }

  private static String read(String location) {
    try (InputStream in =
        McpServerSpecifications.class.getClassLoader().getResourceAsStream(location)) {
      if (in == null) {
        throw new IllegalStateException("Missing " + location);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
