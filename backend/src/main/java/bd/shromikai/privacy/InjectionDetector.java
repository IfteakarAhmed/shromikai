package bd.shromikai.privacy;

import bd.shromikai.shared.text.BanglaNormalizer;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Flags instruction-like text in a clause (FR-34, R-06). A flag never stops the analysis: the
 * clause is analysed as data and the flag {@code INSTRUCTION_LIKE_TEXT} is shown on the clause and
 * in the trace. The detector sees redacted or original text and keeps nothing of it.
 */
@Component
public final class InjectionDetector {

  private static final int FLAGS = Pattern.UNICODE_CASE;

  private static final List<Pattern> PATTERNS =
      List.of(
          p(
              "(ignore|disregard|forget|override|bypass)\\W+(all\\W+|any\\W+|the\\W+|your\\W+|every\\W+)*"
                  + "(previous|prior|above|earlier|preceding|system|these|those|other)?\\W*"
                  + "(instructions?|prompts?|rules?|guidelines?|directions?)"),
          p("ignore\\W+(everything|all)\\b"),
          p("forget\\W+(everything|all)\\b"),
          p("\\byou\\W+are\\W+now\\b"),
          p("\\bsystem\\W+prompt\\b"),
          p("\\b(new|updated|revised)\\W+instructions?\\b"),
          p(
              "\\b(report|find|flag|mention|list)\\W+(no|zero|not\\W+any)\\W+(risks?|issues?|problems?)"),
          p(
              "\\bdo\\W+not\\W+(report|flag|mention|list|identify)\\W+(any\\W+)?(risks?|issues?|problems?)"),
          p("\\b(developer|jailbreak|dan)\\W+mode\\b"),
          p("\\bact\\W+as\\W+(an?\\W+)?(different|unrestricted|new)\\b"),
          p("\\bas\\W+an\\W+ai\\W+(language\\W+)?model\\b"),
          // Bangla
          p(
              "(আগের|পূর্ববর্তী|পূর্বের|সব|সকল|উপরের)\\s*(নির্দেশ|নির্দেশনা|নিয়ম)\\w*\\s*(সব\\s*)?(উপেক্ষা|অগ্রাহ্য|ভুলে)"),
          p("(নির্দেশ|নির্দেশনা)\\w*\\s*(উপেক্ষা|অগ্রাহ্য)"),
          p("তুমি\\s+এখন"),
          p("সিস্টেম\\s*প্রম্পট"),
          p("(কোনো|কোন)\\s*ঝুঁকি\\s*(জানিও|বলো|দেখিও)\\s*না"),
          p("ঝুঁকি\\s*(রিপোর্ট|প্রতিবেদন)\\s*(করো|কোরো)\\s*না"));

  private static Pattern p(String regex) {
    return Pattern.compile(regex, FLAGS | Pattern.CASE_INSENSITIVE);
  }

  /** {@code true} if the text looks like it tries to instruct the model. */
  public boolean isInstructionLike(String text) {
    String form = BanglaNormalizer.matchingForm(text);
    return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(form).find());
  }
}
