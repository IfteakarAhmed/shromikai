package bd.shromikai.analysis;

import bd.shromikai.shared.problem.ProblemException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The progress events of a job (api.md §4.2, FR-58), derived from its in-memory trace: one {@code
 * stage} event per finished step, then one {@code warning} per warning, then {@code completed} or
 * {@code failed}. Events are numbered from 1 in that order and never change, so a client that
 * reconnects with {@code Last-Event-ID: n} gets events n+1 onward. Messages are localised (Bangla
 * unless the client asked for English). The events carry no contract text.
 */
@Component
public final class AnalysisEvents {

  /** One server-sent event: its number, its name and its JSON data. */
  public record Event(int id, String name, ObjectNode data) {

    public boolean last() {
      return name.equals("completed") || name.equals("failed");
    }
  }

  private static final Map<Stage, Integer> PROGRESS =
      Map.ofEntries(
          Map.entry(Stage.INTAKE, 5),
          Map.entry(Stage.OCR, 15),
          Map.entry(Stage.REDACT, 25),
          Map.entry(Stage.CLASSIFY, 35),
          Map.entry(Stage.RETRIEVE, 45),
          Map.entry(Stage.RULES, 50),
          Map.entry(Stage.RISK, 65),
          Map.entry(Stage.VERIFY, 80),
          Map.entry(Stage.CORRECT, 85),
          Map.entry(Stage.COMPOSE, 95),
          Map.entry(Stage.COMPLETE, 100));

  private static final Map<Stage, String> BANGLA =
      Map.ofEntries(
          Map.entry(Stage.INTAKE, "চুক্তি পড়া হচ্ছে"),
          Map.entry(Stage.OCR, "ছবি থেকে লেখা বের করা হচ্ছে"),
          Map.entry(Stage.REDACT, "ব্যক্তিগত তথ্য সরানো হচ্ছে"),
          Map.entry(Stage.CLASSIFY, "ধারাগুলো চেনা হচ্ছে"),
          Map.entry(Stage.RETRIEVE, "প্রাসঙ্গিক আইন খোঁজা হচ্ছে"),
          Map.entry(Stage.RULES, "নিয়ম মিলিয়ে দেখা হচ্ছে"),
          Map.entry(Stage.RISK, "ঝুঁকি বিশ্লেষণ হচ্ছে"),
          Map.entry(Stage.VERIFY, "আইনের উদ্ধৃতি যাচাই হচ্ছে"),
          Map.entry(Stage.CORRECT, "অনিশ্চিত উদ্ধৃতি আবার খোঁজা হচ্ছে"),
          Map.entry(Stage.COMPOSE, "মেমো তৈরি হচ্ছে"),
          Map.entry(Stage.COMPLETE, "সম্পন্ন"));

  private static final Map<Stage, String> ENGLISH =
      Map.ofEntries(
          Map.entry(Stage.INTAKE, "Reading the contract"),
          Map.entry(Stage.OCR, "Reading text from the images"),
          Map.entry(Stage.REDACT, "Removing personal data"),
          Map.entry(Stage.CLASSIFY, "Identifying the clauses"),
          Map.entry(Stage.RETRIEVE, "Finding the relevant law"),
          Map.entry(Stage.RULES, "Checking the rules"),
          Map.entry(Stage.RISK, "Analysing the risks"),
          Map.entry(Stage.VERIFY, "Verifying the citations"),
          Map.entry(Stage.CORRECT, "Looking again for uncertain citations"),
          Map.entry(Stage.COMPOSE, "Preparing the memo"),
          Map.entry(Stage.COMPLETE, "Done"));

  private final ObjectMapper mapper;

  public AnalysisEvents(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** The events of the job so far, in order; the last one is final once the job has ended. */
  public List<Event> of(AnalysisJob job, boolean english) {
    List<Event> events = new ArrayList<>();
    for (TraceEvent trace : job.trace()) {
      if (TraceEvent.FAILED.equals(trace.outcome())) {
        continue; // the failure itself is the final event
      }
      ObjectNode data = mapper.createObjectNode();
      data.put("analysisId", job.id().toString());
      data.put("stage", trace.stage().name());
      data.put("state", "COMPLETED");
      data.put("progress", PROGRESS.get(trace.stage()));
      data.put("message", (english ? ENGLISH : BANGLA).get(trace.stage()));
      data.put("at", trace.at().toString());
      events.add(new Event(events.size() + 1, "stage", data));
    }
    AnalysisJob.Outcome outcome = job.outcome();
    boolean completeRecorded = job.trace().stream().anyMatch(t -> t.stage() == Stage.COMPLETE);
    if (outcome != null && !completeRecorded) {
      return events; // the job is just finishing: keep the numbering stable
    }
    if (outcome != null) {
      for (AnalysisJob.Warning warning : outcome.warnings()) {
        ObjectNode data = mapper.createObjectNode();
        data.put("code", warning.code());
        data.put("message", warning.message());
        events.add(new Event(events.size() + 1, "warning", data));
      }
      ObjectNode data = mapper.createObjectNode();
      data.put("analysisId", job.id().toString());
      data.put("resultUrl", "/api/v1/analyses/" + job.id());
      data.put("findingCount", outcome.findings().size());
      data.put(
          "uncertainCount",
          outcome.findings().stream()
              .filter(f -> f.verification() == FindingVerification.UNCERTAIN)
              .count());
      events.add(new Event(events.size() + 1, "completed", data));
    } else {
      ProblemException failure = job.failure();
      if (failure != null) {
        ObjectNode data = mapper.createObjectNode();
        data.put("type", failure.type().typeUri());
        data.put(
            "title",
            failure.type().title(english ? java.util.Locale.ENGLISH : java.util.Locale.of("bn")));
        data.put("status", failure.type().status());
        data.put("code", failure.type().code());
        events.add(new Event(events.size() + 1, "failed", data));
      }
    }
    return events;
  }

  /** The overall progress of a job, 0 to 100, from its last stage event. */
  public int progress(AnalysisJob job) {
    int progress = 0;
    for (TraceEvent trace : job.trace()) {
      if (!TraceEvent.FAILED.equals(trace.outcome())) {
        progress = Math.max(progress, PROGRESS.get(trace.stage()));
      }
    }
    return job.outcome() != null ? 100 : progress;
  }
}
