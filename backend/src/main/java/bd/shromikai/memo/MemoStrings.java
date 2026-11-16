package bd.shromikai.memo;

/** The fixed memo strings in Bangla and English (R-11). No legal content (R-10). */
final class MemoStrings {

  private final boolean bn;

  MemoStrings(String language) {
    this.bn = "bn".equals(language);
  }

  private String pick(String banglaText, String english) {
    return bn ? banglaText : english;
  }

  String title() {
    return pick("চুক্তি বিশ্লেষণ স্মারক", "Contract analysis memo");
  }

  String summaryHeading() {
    return pick("সারসংক্ষেপ", "Summary");
  }

  String riskTableHeading() {
    return pick("ঝুঁকির তালিকা", "Risk table");
  }

  String detailsHeading() {
    return pick("প্রতিটি ঝুঁকির ব্যাখ্যা", "Each risk explained");
  }

  String revisedHeading() {
    return pick("প্রস্তাবিত সংশোধিত ধারা", "Suggested revised clauses");
  }

  String obligationsHeading() {
    return pick("কর্তব্যের তালিকা", "Obligations checklist");
  }

  String nextStepsHeading() {
    return pick("পরবর্তী পদক্ষেপ", "Next steps");
  }

  String methodHeading() {
    return pick("যাচাই ও পদ্ধতি", "Verification and method");
  }

  String disclaimerHeading() {
    return pick("দায়মুক্তি", "Disclaimer");
  }

  String policyLabel() {
    return pick(
        "কোম্পানির নীতি (আইন নয়): এটি প্রতিষ্ঠানের নিজস্ব নিয়মের সঙ্গে অমিল।",
        "Company policy (not law): this differs from the organisation's own rule.");
  }

  String uncertainLabel() {
    return pick(
        "অনিশ্চিত: কোনো যাচাইকৃত ধারা পাওয়া যায়নি, তাই মানুষ পর্যালোচনা করবেন।",
        "Uncertain: no verified section was found, so a person will review it.");
  }

  String noRedlines() {
    return pick("কোনো সংশোধিত ধারা প্রস্তাব করা হয়নি।", "No revised clause was suggested.");
  }

  String noObligations() {
    return pick("কর্তব্যের তালিকা তৈরি করা যায়নি।", "The obligations checklist was not produced.");
  }

  String numberHeading() {
    return "#";
  }

  String riskColumn() {
    return pick("ঝুঁকি", "Risk");
  }

  String severityColumn() {
    return pick("মাত্রা", "Severity");
  }

  String statusColumn() {
    return pick("অবস্থা", "Status");
  }

  String partyColumn() {
    return pick("পক্ষ", "Party");
  }

  String obligationColumn() {
    return pick("দায়িত্ব", "Obligation");
  }

  String whenColumn() {
    return pick("কখন", "When");
  }

  String basisColumn() {
    return pick("ভিত্তি", "Basis");
  }

  /** CRITICAL, HIGH, MEDIUM, LOW in the reader's language (same words as the web app). */
  String severity(String value) {
    return switch (value) {
      case "CRITICAL" -> pick("গুরুতর", "Critical");
      case "HIGH" -> pick("উচ্চ ঝুঁকি", "High risk");
      case "MEDIUM" -> pick("মনোযোগ দিন", "Look closely");
      case "LOW" -> pick("সামান্য", "Minor");
      default -> value;
    };
  }

  /** VERIFIED, UNCERTAIN, POLICY_ONLY. */
  String verification(String value) {
    return switch (value) {
      case "VERIFIED" -> pick("✓ যাচাইকৃত", "✓ Verified");
      case "UNCERTAIN" -> pick("? অনিশ্চিত", "? Uncertain");
      case "POLICY_ONLY" -> pick("নীতি (আইন নয়)", "Policy (not law)");
      default -> value;
    };
  }

  /** EMPLOYER, WORKER, BOTH. */
  String party(String value) {
    return switch (value) {
      case "EMPLOYER" -> pick("নিয়োগকর্তা", "Employer");
      case "WORKER" -> pick("কর্মী", "Worker");
      case "BOTH" -> pick("দুই পক্ষ", "Both sides");
      default -> value;
    };
  }

  /** CONTRACT, STATUTE, BOTH: where an obligation comes from. */
  String basis(String value) {
    return switch (value) {
      case "CONTRACT" -> pick("চুক্তি", "Contract");
      case "STATUTE" -> pick("আইন", "Statute");
      case "BOTH" -> pick("চুক্তি ও আইন", "Contract and statute");
      default -> value;
    };
  }

  /** DAYS, WEEKS, MONTHS. */
  String unit(String value) {
    return switch (value) {
      case "DAYS" -> pick("দিন", "days");
      case "WEEKS" -> pick("সপ্তাহ", "weeks");
      case "MONTHS" -> pick("মাস", "months");
      default -> value;
    };
  }

  /** DIFFERENT_FAMILY, SAME_FAMILY, MIXED, NONE. */
  String independence(String value) {
    return switch (value) {
      case "DIFFERENT_FAMILY" ->
          pick("ভিন্ন মডেল পরিবারের যাচাইকারী", "a verifier of a different model family");
      case "SAME_FAMILY" ->
          pick("একই মডেল পরিবারের যাচাইকারী", "a verifier of the same model family");
      case "MIXED" -> pick("মিশ্র যাচাইকারী", "a mix of verifiers");
      case "NONE" -> pick("কোনো যাচাইকারী ছিল না", "no verifier");
      default -> value;
    };
  }

  /** R-12: in demo mode the memo says which parts are recorded and which are scripted. */
  String demoNote() {
    return pick(
        "ডেমো মোড (নমুনা তথ্য, রেকর্ড করা): ঝুঁকি খোঁজা, উদ্ধৃতি যাচাই ও প্রস্তাবিত ধারা আগে রেকর্ড করা"
            + " মডেল-উত্তর থেকে। ব্যক্তিগত তথ্য শনাক্ত, দায়িত্ব-তালিকা এবং এই স্মারকের সারাংশ ও পরবর্তী"
            + " পদক্ষেপ স্ক্রিপ্ট করা নমুনা, কোনো মডেলের আসল উত্তর নয়।",
        "Demo mode (sample data, recorded): the risks, the citation checks and the proposed clauses"
            + " come from model answers recorded earlier. The personal-data step, the obligations"
            + " list and this memo's summary and next steps are scripted stand-ins, not real model"
            + " answers.");
  }

  String method(
      int verifiedCitations, int uncertain, int policy, String independence, String corpus) {
    independence = independence(independence);
    if (bn) {
      return "যাচাইকৃত উদ্ধৃতি: "
          + verifiedCitations
          + "। অনিশ্চিত ঝুঁকি: "
          + uncertain
          + "। কোম্পানির নীতির ঝুঁকি: "
          + policy
          + "। যাচাইকারীর স্বাধীনতা: "
          + independence
          + "। আইনের সংকলন: "
          + corpus
          + "। শুধু যাচাইকৃত ধারা দেখানো হয়েছে।";
    }
    return "Verified citations: "
        + verifiedCitations
        + ". Uncertain risks: "
        + uncertain
        + ". Company-policy risks: "
        + policy
        + ". Verifier independence: "
        + independence
        + ". Statute corpus: "
        + corpus
        + ". Only verified sections are shown.";
  }

  String disclaimer() {
    return pick(
        "এটা আইনি পরামর্শ নয়। এই স্মারক শুধু তথ্যের জন্য; সিদ্ধান্তের আগে একজন আইনজীবী বা শ্রম"
            + " কার্যালয়ের সঙ্গে কথা বলুন।",
        "This is not legal advice. This memo is for information only; speak to a lawyer or a labour"
            + " office before you decide.");
  }
}
