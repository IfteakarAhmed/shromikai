package bd.shromikai.shared.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/**
 * Structured JSON log lines that carry only allowlisted keys (architecture §15, NFR-18).
 *
 * <ul>
 *   <li>Project loggers ({@code bd.shromikai.*}) emit the message <em>template</em>, never the
 *       formatted arguments, so values reach the log only as allowlisted key-value pairs.
 *   <li>Third-party messages and every value pass through {@link LogRedactor}.
 *   <li>Throwables are reduced to {@code reasonCode} = exception class name: exception messages may
 *       hold contract text (R-02).
 * </ul>
 */
public final class AllowlistJsonFormatter implements StructuredLogFormatter<ILoggingEvent> {

  private static final JsonFactory JSON = new JsonFactory();
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  private static final String PROJECT_LOGGER_PREFIX = "bd.shromikai.";

  @Override
  public String format(ILoggingEvent event) {
    StringWriter out = new StringWriter(256);
    try (JsonGenerator json = JSON.createGenerator(out)) {
      json.writeStartObject();
      json.writeStringField(
          LogKeys.TIMESTAMP, TIMESTAMP.format(Instant.ofEpochMilli(event.getTimeStamp())));
      json.writeStringField(LogKeys.LEVEL, event.getLevel().toString());
      json.writeStringField(LogKeys.LOGGER, event.getLoggerName());
      json.writeStringField(LogKeys.MESSAGE, LogRedactor.redact(message(event)));
      for (Map.Entry<String, String> field : fields(event).entrySet()) {
        json.writeStringField(field.getKey(), LogRedactor.redact(field.getValue()));
      }
      json.writeEndObject();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.append('\n').toString();
  }

  private static String message(ILoggingEvent event) {
    String text =
        event.getLoggerName().startsWith(PROJECT_LOGGER_PREFIX)
            ? event.getMessage()
            : event.getFormattedMessage();
    return text == null ? "" : text;
  }

  /** Allowlisted MDC and key-value fields in a stable order. Unknown keys are dropped. */
  static Map<String, String> fields(ILoggingEvent event) {
    Map<String, String> fields = new TreeMap<>();
    Map<String, String> mdc = event.getMDCPropertyMap();
    if (mdc != null) {
      mdc.forEach((key, value) -> put(fields, key, value));
    }
    if (event.getKeyValuePairs() != null) {
      for (KeyValuePair pair : event.getKeyValuePairs()) {
        put(fields, pair.key, pair.value == null ? null : String.valueOf(pair.value));
      }
    }
    if (event.getThrowableProxy() != null && !fields.containsKey(LogKeys.REASON_CODE)) {
      String className = event.getThrowableProxy().getClassName();
      fields.put(LogKeys.REASON_CODE, className.substring(className.lastIndexOf('.') + 1));
    }
    return fields;
  }

  private static void put(Map<String, String> fields, String key, String value) {
    if (value != null && LogKeys.ALL.contains(key) && !LogKeys.RESERVED.contains(key)) {
      fields.put(key, value);
    }
  }
}
