package bd.shromikai.eval.harness;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * M-17 for one evaluation step (same checks as {@code NoContractPersistenceIT}): after the step, no
 * text or JSON column of the database, no log line of the step and no new temp file holds a canary
 * value or 8 consecutive tokens of an evaluated contract. Reports places, never values.
 */
public final class AtRestScanner {

  private static final int WINDOW = 8;

  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final Set<Path> tempBefore;

  public AtRestScanner() {
    logs.start();
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    tempBefore = listTemp();
  }

  /** The places that hold a canary or a window (empty when clean), and the places scanned. */
  public record Result(List<String> offenders, List<String> scanned) {}

  public Result scan(JdbcClient jdbc, List<String> documents, List<String> secrets) {
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    Set<String> windows = new HashSet<>();
    documents.forEach(d -> windows.addAll(windowsOf(d)));
    List<String> offenders = new ArrayList<>();
    List<String> scanned = new ArrayList<>();
    List<Map<String, Object>> columns =
        jdbc.sql(
                """
                select c.table_name, c.column_name
                from information_schema.columns c
                join information_schema.tables t
                  on t.table_schema = c.table_schema and t.table_name = c.table_name
                where c.table_schema = 'public' and t.table_type = 'BASE TABLE'
                  and c.data_type in ('text', 'character varying', 'character', 'jsonb', 'json', 'ARRAY')
                  and c.table_name not in ('law_section', 'law_act', 'law_term', 'section_edge',
                                           'corpus_release', 'flyway_schema_history')
                """)
            .query()
            .listOfRows();
    for (Map<String, Object> column : columns) {
      String table = (String) column.get("table_name");
      String name = (String) column.get("column_name");
      scanned.add("db:" + table + "." + name);
      for (String value :
          jdbc.sql(
                  "select cast(\""
                      + name
                      + "\" as text) from \""
                      + table
                      + "\" where \""
                      + name
                      + "\" is not null")
              .query(String.class)
              .list()) {
        if (hit(value, windows, secrets)) {
          offenders.add("db " + table + "." + name);
        }
      }
    }
    scanned.add("logs:" + logs.list.size() + " events");
    for (ILoggingEvent event : new ArrayList<>(logs.list)) {
      StringBuilder sb = new StringBuilder(event.getFormattedMessage());
      event.getMDCPropertyMap().forEach((k, v) -> sb.append(' ').append(k).append('=').append(v));
      if (event.getThrowableProxy() != null) {
        sb.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
      }
      if (hit(sb.toString(), windows, secrets)) {
        offenders.add("log " + event.getLoggerName());
      }
    }
    logs.stop();
    int files = 0;
    for (Path file : listTemp()) {
      try {
        if (tempBefore.contains(file)
            || !Files.isRegularFile(file)
            || Files.size(file) > 8_000_000) {
          continue;
        }
        files++;
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        if (hit(text, windows, secrets)) {
          offenders.add("temp " + file.getFileName());
        }
      } catch (IOException e) {
        // unreadable files of the shared temp folder are not ours
      }
    }
    scanned.add("temp:" + files + " new files");
    return new Result(offenders, scanned);
  }

  private static boolean hit(String value, Set<String> windows, List<String> secrets) {
    if (CloudCapture.containsAny(Normalizer.normalize(value, Normalizer.Form.NFC), secrets)) {
      return true;
    }
    for (String w : windowsOf(value)) {
      if (windows.contains(w)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> windowsOf(String text) {
    String[] tokens = Normalizer.normalize(text, Normalizer.Form.NFC).trim().split("\\s+");
    Set<String> out = new HashSet<>();
    for (int i = 0; i + WINDOW <= tokens.length; i++) {
      out.add(String.join(" ", java.util.Arrays.copyOfRange(tokens, i, i + WINDOW)));
    }
    return out;
  }

  private static Set<Path> listTemp() {
    Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
    Set<Path> out = new HashSet<>();
    try (Stream<Path> walk = Files.walk(tmp, 4)) {
      walk.forEach(out::add);
    } catch (IOException | java.io.UncheckedIOException e) {
      // unreadable sub-directories of the shared temp dir are not ours
    }
    return out;
  }
}
