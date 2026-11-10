package bd.shromikai.testsupport.archsamples.analysis;

import org.springframework.ai.tool.annotation.Tool;

/** Declares an MCP tool outside mcp (rule 6). */
public final class ToolOutsideMcp {

  @Tool(description = "sample")
  public String sample() {
    return "x";
  }
}
