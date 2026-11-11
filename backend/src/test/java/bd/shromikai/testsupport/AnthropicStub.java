package bd.shromikai.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A WireMock transformer that plays the Claude Messages API for the two P5 cloud prompts ({@code
 * clause-classify}, {@code clause-segment}). It reads the clauses out of the request and answers
 * with schema-valid JSON, so the real {@code AnthropicChatAdapter} runs end to end.
 */
public final class AnthropicStub implements ResponseDefinitionTransformerV2 {

  public static final String NAME = "anthropic-stub";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern CLAUSES =
      Pattern.compile("<contract_clause>\\s*(.*?)\\s*</contract_clause>", Pattern.DOTALL);

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public ResponseDefinition transform(ServeEvent event) {
    try {
      JsonNode request = MAPPER.readTree(event.getRequest().getBodyAsString());
      List<String> strings = new ArrayList<>();
      collect(request, strings);
      String all = String.join("\n", strings);
      ObjectNode answer = answer(all);
      ObjectNode message = MAPPER.createObjectNode();
      message.put("id", "msg_stub").put("type", "message").put("role", "assistant");
      message.put("model", "claude-haiku-5-5").put("stop_reason", "end_turn");
      message.putNull("stop_sequence");
      ArrayNode content = message.putArray("content");
      content.addObject().put("type", "text").put("text", answer.toString());
      message.putObject("usage").put("input_tokens", 10).put("output_tokens", 5);
      return com.github.tomakehurst.wiremock.client.WireMock.aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(MAPPER.writeValueAsString(message))
          .build();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** The schema-valid answer for a rendered request text (system and user turn together). */
  public static ObjectNode answer(String requestText) {
    try {
      Matcher m = CLAUSES.matcher(requestText);
      String json = m.find() ? unescape(m.group(1)) : "[]";
      JsonNode items = MAPPER.readTree(json);
      boolean segment = requestText.contains("split an employment contract");
      return segment ? segment(items) : classify(items);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static ObjectNode classify(JsonNode items) {
    ObjectNode out = MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE");
    String first =
        items.isEmpty() ? "" : items.get(0).path("text").asText().toLowerCase(Locale.ROOT);
    boolean rental =
        first.contains("rental") || first.contains("tenant") || first.contains("ভাড়াটিয়া");
    out.put("document_type", rental ? "OTHER_AGREEMENT" : "EMPLOYMENT_CONTRACT");
    ArrayNode clauses = out.putArray("clauses");
    for (JsonNode item : items) {
      clauses
          .addObject()
          .put("id", item.path("id").asText())
          .put("type", typeOf(item.path("text").asText()))
          .put("confidence", 0.9);
    }
    return out;
  }

  private static ObjectNode segment(JsonNode items) {
    ObjectNode out = MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode clauses = out.putArray("clauses");
    for (JsonNode item : items) {
      clauses.addObject().putArray("paragraph_ids").add(item.path("id").asText());
    }
    return out;
  }

  /** Keyword typing for the synthetic contracts; good enough to exercise the pipeline. */
  static String typeOf(String text) {
    String t = text.toLowerCase(Locale.ROOT);
    if (t.contains("probation") || t.contains("শিক্ষানবিশ")) return "PROBATION";
    if (t.contains("overtime") || t.contains("অতিরিক্ত সময়")) return "OVERTIME";
    if (t.contains("notice") || t.contains("নোটিশ")) return "NOTICE_AND_RESIGNATION";
    if (t.contains("non-compet") || t.contains("প্রতিযোগিতা")) return "NON_COMPETE";
    if (t.contains("dispute") || t.contains("বিরোধ")) return "DISPUTE_RESOLUTION";
    if (t.contains("wage") || t.contains("salary") || t.contains("মজুরি") || t.contains("বেতন"))
      return "WAGES";
    if (t.contains("hours") || t.contains("কর্মঘণ্টা")) return "WORKING_HOURS";
    if (t.contains("leave") || t.contains("ছুটি")) return "LEAVE";
    return "OTHER";
  }

  private static void collect(JsonNode node, List<String> out) {
    if (node.isTextual()) {
      out.add(node.asText());
    } else {
      node.forEach(child -> collect(child, out));
    }
  }

  private static String unescape(String s) {
    return s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
  }
}
