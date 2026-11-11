package bd.shromikai.mcp;

import bd.shromikai.auth.ApiKeyPrincipal;
import bd.shromikai.mcp.internal.McpCallGuard;
import bd.shromikai.mcp.internal.McpProperties;
import bd.shromikai.mcp.internal.McpServerSpecifications;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The MCP server of ShromikAI (FR-64, ADR-0021): Streamable HTTP at {@code /mcp}, switched on by
 * the profile {@code mcp}. Spring AI's auto-configuration builds the server; this class gives it
 * the tools, the resource template and the prompt, and a transport whose context extractor hands
 * the authenticated API key (set by the security filter of {@code api}) to every call.
 */
@Configuration(proxyBeanMethods = false)
@Profile("mcp")
@EnableConfigurationProperties(McpProperties.class)
public class McpServerConfiguration {

  @Bean
  McpJsonMapper mcpJsonMapper(@Qualifier("mcpServerObjectMapper") ObjectMapper mapper) {
    return new JacksonMcpJsonMapper(mapper);
  }

  /** Same as the starter's transport, plus the context extractor. */
  @Bean
  WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
      McpJsonMapper mapper, McpServerStreamableHttpProperties properties) {
    return WebMvcStreamableServerTransportProvider.builder()
        .jsonMapper(mapper)
        .mcpEndpoint(properties.getMcpEndpoint())
        .keepAliveInterval(properties.getKeepAliveInterval())
        .contextExtractor(
            request -> {
              Authentication authentication =
                  SecurityContextHolder.getContext().getAuthentication();
              if (authentication != null
                  && authentication.getPrincipal() instanceof ApiKeyPrincipal key) {
                return McpTransportContext.create(Map.of(McpCallGuard.PRINCIPAL, key));
              }
              return McpTransportContext.EMPTY;
            })
        .build();
  }

  @Bean
  List<McpServerFeatures.SyncToolSpecification> shromikaiTools(McpServerSpecifications specs) {
    return specs.tools();
  }

  @Bean
  List<McpServerFeatures.SyncResourceTemplateSpecification> shromikaiResourceTemplates(
      McpServerSpecifications specs) {
    return specs.resourceTemplates();
  }

  @Bean
  List<McpServerFeatures.SyncPromptSpecification> shromikaiPrompts(McpServerSpecifications specs) {
    return specs.prompts();
  }
}
