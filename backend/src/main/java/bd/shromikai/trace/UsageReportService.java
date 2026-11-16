package bd.shromikai.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * The admin usage and cost report with the KPI-12 to KPI-14 block (FR-73, PRD §10). It reads
 * counts, costs and timestamps only; no table it reads holds contract text (R-02). Days are
 * calendar days in Asia/Dhaka. The KPIs always cover the seven days that end at {@code to},
 * whatever the length of the chart window, and a ratio over no data is {@code null}, never zero.
 */
@Service
public class UsageReportService {

  /** The longest window a report may cover. */
  public static final int MAX_DAYS = 92;

  /** KPI window in days (PRD §10). */
  public static final int KPI_DAYS = 7;

  public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

  /** Failures that say nothing about the product: the input was not a usable contract. */
  private static final String EXCLUDED_FAILURES =
      "('not-an-employment-contract', 'unreadable-document')";

  private static final double MICROS = 1_000_000.0;

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  public UsageReportService(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  /**
   * @param orgId restricts every figure to one organisation; {@code null} covers all
   * @throws IllegalArgumentException when the window is backwards or longer than {@link #MAX_DAYS}
   */
  public ObjectNode report(@Nullable UUID orgId, LocalDate from, LocalDate to) {
    long length = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
    if (length < 1 || length > MAX_DAYS) {
      throw new IllegalArgumentException("The window must cover 1 to " + MAX_DAYS + " days");
    }
    Timestamp start = Timestamp.from(from.atStartOfDay(ZONE).toInstant());
    Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant());

    Map<LocalDate, Day> days = new TreeMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      days.put(d, new Day());
    }
    String orgFilter = orgId == null ? "" : " and org_id = :org";

    var events =
        jdbc.sql(
            "select (occurred_at at time zone 'Asia/Dhaka')::date as day, kind,"
                + " sum(units) as units, sum(cost_usd_micros) as cost from usage_event"
                + " where occurred_at >= :start and occurred_at < :end"
                + orgFilter
                + " group by 1, 2");
    events = events.param("start", start).param("end", end);
    if (orgId != null) {
      events = events.param("org", orgId);
    }
    events
        .query(
            (rs, n) -> {
              Day day = days.get(rs.getDate("day").toLocalDate());
              if (day != null) {
                int units = rs.getInt("units");
                switch (rs.getString("kind")) {
                  case "ANALYSIS" -> day.analyses += units;
                  case "COMPARE" -> day.compares += units;
                  case "MEMO_RENDER" -> day.memoRenders += units;
                  case "API_CALL" -> day.apiCalls += units;
                  case "MCP_CALL" -> day.mcpCalls += units;
                  default -> {}
                }
                day.costMicros += rs.getLong("cost");
              }
              return null;
            })
        .list();

    String traceOrg = orgId == null ? "" : " and r.org_id = :org";
    var models =
        jdbc.sql(
            "select (t.created_at at time zone 'Asia/Dhaka')::date as day, t.model_id,"
                + " sum(t.cost_usd_micros) as cost from analysis_trace t"
                + " join analysis_run r on r.id = t.analysis_id"
                + " where t.model_id is not null and t.cost_usd_micros > 0"
                + " and t.created_at >= :start and t.created_at < :end"
                + traceOrg
                + " group by 1, 2");
    models = models.param("start", start).param("end", end);
    if (orgId != null) {
      models = models.param("org", orgId);
    }
    models
        .query(
            (rs, n) -> {
              Day day = days.get(rs.getDate("day").toLocalDate());
              if (day != null) {
                day.byModel.merge(rs.getString("model_id"), rs.getLong("cost"), Long::sum);
              }
              return null;
            })
        .list();

    ObjectNode report = mapper.createObjectNode();
    report.put("from", from.toString());
    report.put("to", to.toString());
    if (orgId == null) {
      report.putNull("orgId");
    } else {
      report.put("orgId", orgId.toString());
    }
    ArrayNode dayNodes = report.putArray("days");
    Day total = new Day();
    for (Map.Entry<LocalDate, Day> e : days.entrySet()) {
      Day d = e.getValue();
      ObjectNode node = dayNodes.addObject();
      node.put("date", e.getKey().toString());
      d.write(node);
      total.add(d);
    }
    total.write(report.putObject("totals"));
    report.set("kpis", kpis(orgId, to));
    return report;
  }

  private ObjectNode kpis(@Nullable UUID orgId, LocalDate to) {
    LocalDate firstDay = to.minusDays(KPI_DAYS - 1L);
    Timestamp start = Timestamp.from(firstDay.atStartOfDay(ZONE).toInstant());
    Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant());
    String runOrg = orgId == null ? "" : " and org_id = :org";
    String reviewOrg =
        orgId == null
            ? ""
            : " and exists (select 1 from analysis_run r where r.id = i.analysis_id"
                + " and r.org_id = :org)";

    var runs =
        jdbc.sql(
            "select count(*) filter (where status = 'SUCCEEDED') as succeeded,"
                + " count(*) filter (where status = 'FAILED' and (error_code is null"
                + " or error_code not in "
                + EXCLUDED_FAILURES
                + ")) as failed from analysis_run"
                + " where created_at >= :start and created_at < :end"
                + runOrg);
    runs = runs.param("start", start).param("end", end);
    var memos =
        jdbc.sql(
            "select coalesce(sum(units), 0) from usage_event where kind = 'MEMO_RENDER'"
                + " and occurred_at >= :start and occurred_at < :end"
                + runOrg);
    memos = memos.param("start", start).param("end", end);
    var review =
        jdbc.sql(
            "select count(*) as n, percentile_cont(0.5) within group (order by"
                + " extract(epoch from (i.decided_at - i.created_at)) / 3600.0) as median"
                + " from review_item i where i.decided_at is not null"
                + " and i.decided_at >= :start and i.decided_at < :end"
                + reviewOrg);
    review = review.param("start", start).param("end", end);
    if (orgId != null) {
      runs = runs.param("org", orgId);
      memos = memos.param("org", orgId);
      review = review.param("org", orgId);
    }
    long[] counts =
        runs.query((rs, n) -> new long[] {rs.getLong("succeeded"), rs.getLong("failed")}).single();
    long memoRenders = memos.query(Long.class).single();
    ObjectNode reviewRow =
        review
            .query(
                (rs, n) -> {
                  ObjectNode node = mapper.createObjectNode();
                  node.put("n", rs.getLong("n"));
                  double median = rs.getDouble("median");
                  if (rs.wasNull()) {
                    node.putNull("value");
                  } else {
                    node.put("value", median);
                  }
                  return node;
                })
            .single();

    ObjectNode kpis = mapper.createObjectNode();
    kpis.put("windowDays", KPI_DAYS);
    long succeeded = counts[0];
    long decided = counts[0] + counts[1];
    ObjectNode completion = kpis.putObject("completionRate");
    ratio(completion, succeeded, decided);
    ObjectNode memoRate = kpis.putObject("memoDownloadRate");
    ratio(memoRate, memoRenders, succeeded);
    kpis.set("reviewTurnaroundHoursMedian", reviewRow);
    return kpis;
  }

  /** {@code value} is count over n, or null when n is zero. */
  private static void ratio(ObjectNode node, long count, long n) {
    if (n == 0) {
      node.putNull("value");
    } else {
      node.put("value", (double) count / n);
    }
    node.put("count", count);
    node.put("n", n);
  }

  private final class Day {
    int analyses;
    int compares;
    int memoRenders;
    int apiCalls;
    int mcpCalls;
    long costMicros;
    final Map<String, Long> byModel = new LinkedHashMap<>();

    void add(Day other) {
      analyses += other.analyses;
      compares += other.compares;
      memoRenders += other.memoRenders;
      apiCalls += other.apiCalls;
      mcpCalls += other.mcpCalls;
      costMicros += other.costMicros;
      other.byModel.forEach((k, v) -> byModel.merge(k, v, Long::sum));
    }

    void write(ObjectNode node) {
      node.put("analyses", analyses);
      node.put("compares", compares);
      node.put("memoRenders", memoRenders);
      node.put("apiCalls", apiCalls);
      node.put("mcpCalls", mcpCalls);
      node.put("costUsd", costMicros / MICROS);
      ObjectNode models = node.putObject("costByModel");
      new TreeMap<>(byModel).forEach((k, v) -> models.put(k, v / MICROS));
    }
  }
}
