package bd.shromikai.testsupport.archsamples.mcp;

import org.springframework.ai.mcp.ArchSampleMcpType;
import org.springframework.ai.tool.annotation.Tool;

/** Allowed: mcp uses Spring AI MCP and tool types (rule 3 exempts them; P8). */
public final class McpTools {

  @Tool(description = "sample")
  public String sample(ArchSampleMcpType type) {
    return type.toString();
  }
}
