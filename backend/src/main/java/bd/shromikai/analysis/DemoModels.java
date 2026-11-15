package bd.shromikai.analysis;

import bd.shromikai.shared.ai.fake.FakeChatPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The answers of the {@code fake-models} profile that no recording covers (architecture §16.3). The
 * risk analysis, verification, repair, classification, fact and counter-offer calls of the sample
 * contract replay <em>recorded</em> model output; these five steps were never recorded, so they are
 * <strong>scripted stand-ins</strong> and say so:
 *
 * <ul>
 *   <li>{@code pii-extract}: the invented names of the sample contract (its ground truth), found
 *       the way the local model would; any other text has none, so only the pattern detectors act;
 *   <li>{@code obligations}, {@code memo-bn}, {@code memo-en}: short fixed answers about the
 *       sample;
 *   <li>{@code baseline}: a plain-model answer that cites an invented section and a real one with
 *       an invented quote, so compare mode shows what the verifier rejects.
 * </ul>
 *
 * Nothing here states what the law requires; the scripted baseline claims are fabrications on
 * purpose and are rejected by the real verifier (R-01, R-10, R-12).
 */
@Component
@Profile("fake-models")
public class DemoModels {

  /** The recordings were made for the worker's side in Bangla; other requests have no replay. */
  public static final Side RECORDED_SIDE = Side.WORKER;

  public static final String RECORDED_LANGUAGE = "bn";

  /** Model ID of every stand-in, so a result never presents scripted text as a real model's. */
  public static final String STAND_IN_MODEL = "scripted-demo-standin";

  private final FakeChatPort fake;
  private final SampleContract sample;
  private final ObjectMapper mapper;

  public DemoModels(FakeChatPort fake, SampleContract sample, ObjectMapper mapper) {
    this.fake = fake;
    this.sample = sample;
    this.mapper = mapper;
  }

  @PostConstruct
  void install() {
    fake.unrecordedAsProblem(true);
    fake.respondWith("pii-extract", STAND_IN_MODEL, request -> pii(request.user().reveal()));
    fake.respondWith("obligations", STAND_IN_MODEL, request -> obligations());
    fake.respondWith("memo-bn", STAND_IN_MODEL, request -> memo(bnSummary(), bnSteps()));
    fake.respondWith("memo-en", STAND_IN_MODEL, request -> memo(enSummary(), enSteps()));
    fake.respondWith("baseline", STAND_IN_MODEL, request -> baseline());
  }

  private JsonNode pii(String text) {
    ObjectNode out = mapper.createObjectNode().put("status", "OK").put("reason", "NONE");
    ArrayNode persons = out.putArray("persons");
    ArrayNode organisations = out.putArray("organisations");
    ArrayNode addresses = out.putArray("addresses");
    out.putArray("employee_ids");
    for (SampleContract.Entity entity : sample.entities()) {
      if (!text.contains(entity.value())) {
        continue;
      }
      switch (entity.type()) {
        case "PERSON" -> persons.add(entity.value());
        case "ORG" -> organisations.add(entity.value());
        case "ADDRESS" -> addresses.add(entity.value());
        default -> {}
      }
    }
    return out;
  }

  private JsonNode obligations() {
    ObjectNode out = mapper.createObjectNode().put("status", "OK").put("reason", "NONE");
    ArrayNode items = out.putArray("items");
    items.add(
        item(
            "EMPLOYER",
            "Pay the monthly salary stated in the contract",
            "Each month",
            "STATED_IN_CONTRACT"));
    items.add(
        item(
            "WORKER",
            "Work the hours and at the sites set in the contract",
            "During the engagement",
            "STATED_IN_CONTRACT"));
    return out;
  }

  private ObjectNode item(String party, String obligation, String trigger, String status) {
    ObjectNode item = mapper.createObjectNode();
    item.put("party", party);
    item.put("obligation", obligation);
    item.put("trigger", trigger);
    item.putNull("deadline");
    item.put("source", "CONTRACT");
    item.put("status", status);
    item.putArray("clause_ids");
    item.putArray("citations");
    return item;
  }

  private JsonNode memo(String summary, List<String> steps) {
    ObjectNode out = mapper.createObjectNode().put("status", "OK").put("reason", "NONE");
    out.put("summary", summary);
    ArrayNode next = out.putArray("next_steps");
    steps.forEach(next::add);
    return out;
  }

  private JsonNode baseline() {
    ObjectNode out = mapper.createObjectNode().put("status", "OK").put("reason", "NONE");
    ArrayNode findings = out.putArray("findings");
    findings.add(
        finding(
            "Dismissal without notice",
            "HIGH",
            "A plain model says the law lets an employer end the contract at once.",
            "BLA-2006:999@original",
            "An employer may end a worker's service at once and without any notice at all.",
            "The Act lets an employer dismiss a worker without notice."));
    findings.add(
        finding(
            "Unlimited working hours",
            "MEDIUM",
            "A plain model says the law sets no limit on daily hours.",
            "BLA-2006:2@amend-2026",
            "No worker shall have any limit on the hours of work in any day or any week.",
            "The Act sets no limit on the hours of work."));
    return out;
  }

  private ObjectNode finding(
      String title,
      String severity,
      String explanation,
      String sectionKey,
      String quote,
      String claim) {
    ObjectNode finding = mapper.createObjectNode();
    finding.put("title", title);
    finding.put("severity", severity);
    finding.put("explanation", explanation);
    ObjectNode citation = finding.putArray("citations").addObject();
    citation.put("section_key", sectionKey);
    citation.put("quote", quote);
    citation.put("claim", claim);
    return finding;
  }

  private static String bnSummary() {
    return "এটি একটি নমুনা চুক্তির রেকর্ড করা বিশ্লেষণ। চুক্তিতে একাধিক ঝুঁকি পাওয়া গেছে। যেখানে আইনের ধারা"
        + " যাচাই করা গেছে সেখানে উদ্ধৃতি দেখানো হয়েছে; বাকিগুলো মানুষের পর্যালোচনার জন্য পাঠানো হয়েছে।";
  }

  private static List<String> bnSteps() {
    return List.of(
        "প্রতিটি উদ্ধৃতি সরকারি উৎসে মিলিয়ে দেখুন।",
        "প্রস্তাবিত নতুন ধারাগুলো নিয়োগকর্তার সাথে আলোচনা করুন।",
        "সই করার আগে একজন আইনজীবীর সাথে কথা বলুন।");
  }

  private static String enSummary() {
    return "This is a recorded analysis of a sample contract. Several risks were found. Where a"
        + " section of the law could be verified, the citation is shown; the rest were sent for"
        + " human review.";
  }

  private static List<String> enSteps() {
    return List.of(
        "Check every citation against the official source.",
        "Discuss the proposed new clauses with the employer.",
        "Talk to a lawyer before you sign.");
  }
}
