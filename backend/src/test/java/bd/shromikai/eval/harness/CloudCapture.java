package bd.shromikai.eval.harness;

import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.text.BanglaNormalizer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What left (or would have left) for a cloud model during an evaluation run, without keeping it:
 * per request the label of the system being run, the prompt, whether it was replayed from a
 * recording, its priced cost and whether any of the contract's PII values appeared in the rendered
 * request (M-16). The request text itself is scanned and dropped (R-02).
 */
public final class CloudCapture {

  /** One request, content-free. */
  public record Call(
      String label, String promptId, boolean replayed, long costMicros, boolean canaryHit) {}

  private static final List<Call> CALLS = Collections.synchronizedList(new ArrayList<>());
  private static volatile boolean active;
  private static volatile String label = "none";
  private static volatile List<String> canaries = List.of();

  private CloudCapture() {}

  /** Turns capture on (the live runner wraps the cloud ports; tests on fakes leave it off). */
  public static void activate() {
    active = true;
  }

  public static boolean active() {
    return active;
  }

  public static void label(String newLabel) {
    label = newLabel;
  }

  public static void canaries(List<String> values) {
    canaries = List.copyOf(values);
  }

  public static int mark() {
    return CALLS.size();
  }

  public static List<Call> since(int mark) {
    synchronized (CALLS) {
      return List.copyOf(CALLS.subList(mark, CALLS.size()));
    }
  }

  /** Records one request; the text is only scanned. */
  public static void record(ChatRequest request, boolean replayed, long costMicros) {
    CALLS.add(
        new Call(
            label, request.promptId(), replayed, costMicros, containsCanary(request, canaries)));
  }

  /** True when any value (also with Latin digits, or digits only) appears in the request. */
  public static boolean containsCanary(ChatRequest request, List<String> values) {
    String text =
        Normalizer.normalize(
            request.system() + "\n" + request.user().reveal(), Normalizer.Form.NFC);
    return containsAny(text, values);
  }

  public static boolean containsAny(String text, List<String> values) {
    String latin = BanglaNormalizer.toLatinDigits(text);
    // Numbers written with separators ("01700-000101") are compared without them.
    String joined = latin.replaceAll("(?<=\\d)[\\s\\-](?=\\d)", "");
    for (String value : values) {
      String v = Normalizer.normalize(value, Normalizer.Form.NFC);
      if (text.contains(v) || latin.contains(BanglaNormalizer.toLatinDigits(v))) {
        return true;
      }
      String vDigits = BanglaNormalizer.toLatinDigits(v).replaceAll("[^0-9]", "");
      boolean numeric = v.replaceAll("[\\s\\-+()০-৯0-9]", "").isEmpty();
      if (numeric
          && vDigits.length() >= 8
          && java.util.regex.Pattern.compile("(?<!\\d)" + vDigits + "(?!\\d)")
              .matcher(joined)
              .find()) {
        return true;
      }
    }
    return false;
  }
}
