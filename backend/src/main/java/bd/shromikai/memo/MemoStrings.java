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

  String method(
      int verifiedCitations, int uncertain, int policy, String independence, String corpus) {
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
