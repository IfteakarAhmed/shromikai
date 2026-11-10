package bd.shromikai.shared.problem;

import java.util.Locale;

/**
 * Every problem code of the error model (docs/spec/api.md §7) with its HTTP status and titles in
 * Bangla and English. {@code type} is {@code urn:shromikai:problem:{code}}.
 */
public enum ProblemType {
  VALIDATION_FAILED("validation-failed", 400, "অনুরোধটি সঠিক নয়", "Request is invalid"),
  SIGNATURE_INVALID(
      "signature-invalid", 400, "মেমোর স্বাক্ষর সঠিক নয়", "Memo envelope signature is invalid"),
  INVALID_CREDENTIALS(
      "invalid-credentials", 401, "ইমেইল বা পাসওয়ার্ড ভুল", "Email or password is wrong"),
  INVALID_TOKEN("invalid-token", 401, "সাইন-ইনের মেয়াদ শেষ", "Sign-in has expired"),
  FORBIDDEN("forbidden", 403, "অনুমতি নেই", "Not allowed"),
  DEMO_ACCOUNT_RESTRICTED(
      "demo-account-restricted",
      403,
      "ডেমো অ্যাকাউন্টে এটি করা যাবে না",
      "Not available for demo accounts"),
  NOT_FOUND("not-found", 404, "পাওয়া যায়নি", "Not found"),
  METHOD_NOT_ALLOWED(
      "method-not-allowed", 405, "এই পদ্ধতি অনুমোদিত নয়", "Method not allowed for this resource"),
  NOT_ACCEPTABLE(
      "not-acceptable", 406, "এই ফরম্যাটে উত্তর দেওয়া যায় না", "Response format not available"),
  EMAIL_TAKEN("email-taken", 409, "এই ইমেইল আগেই নিবন্ধিত", "Email already registered"),
  IDEMPOTENCY_KEY_REUSED(
      "idempotency-key-reused",
      409,
      "একই কী ভিন্ন অনুরোধে ব্যবহৃত",
      "Idempotency key reused with a different request"),
  ALREADY_CLAIMED(
      "already-claimed", 409, "অন্য কেউ পর্যালোচনা করছেন", "Review item already claimed"),
  ALREADY_DECIDED("already-decided", 409, "সিদ্ধান্ত আগেই হয়েছে", "Review item already decided"),
  INGEST_RUNNING("ingest-running", 409, "আইন সংগ্রহ চলছে", "Ingestion already running"),
  ANALYSIS_EXPIRED("analysis-expired", 410, "ফলাফলের মেয়াদ শেষ", "Result has expired"),
  ENVELOPE_EXPIRED("envelope-expired", 410, "মেমোর মেয়াদ শেষ", "Memo link has expired"),
  TICKET_EXPIRED(
      "ticket-expired", 410, "পর্যালোচনা টিকিটের মেয়াদ শেষ", "Review ticket has expired"),
  PAYLOAD_TOO_LARGE("payload-too-large", 413, "ফাইলটি অনেক বড়", "File is too large"),
  UNSUPPORTED_MEDIA_TYPE(
      "unsupported-media-type", 415, "এই ধরনের ফাইল গ্রহণযোগ্য নয়", "File type not supported"),
  TOO_MANY_PAGES("too-many-pages", 422, "পৃষ্ঠা সংখ্যা বেশি", "Too many pages"),
  UNREADABLE_DOCUMENT(
      "unreadable-document", 422, "কিছু পৃষ্ঠা পড়া যায়নি", "Some pages could not be read"),
  NOT_AN_EMPLOYMENT_CONTRACT(
      "not-an-employment-contract",
      422,
      "এটি চাকরির চুক্তি বলে মনে হচ্ছে না",
      "This does not look like an employment contract"),
  CITATION_NOT_VERIFIABLE(
      "citation-not-verifiable",
      422,
      "উদ্ধৃতি যাচাই করা যায়নি",
      "Citation failed existence or quote check"),
  INVALID_TRANSITION(
      "invalid-transition", 422, "অবস্থা পরিবর্তন অনুমোদিত নয়", "Status change not allowed"),
  PLAYBOOK_LIMIT_REACHED(
      "playbook-limit-reached", 422, "প্লেবুকের সীমা পূর্ণ", "Playbook limit reached"),
  ACCOUNT_LOCKED(
      "account-locked", 423, "অ্যাকাউন্ট সাময়িকভাবে বন্ধ", "Account temporarily locked"),
  QUOTA_EXCEEDED("quota-exceeded", 429, "আজকের সীমা শেষ", "Daily limit reached"),
  CONCURRENCY_LIMIT(
      "concurrency-limit", 429, "একটি বিশ্লেষণ চলছে", "An analysis is already running"),
  RATE_LIMITED("rate-limited", 429, "অনেক বেশি অনুরোধ", "Too many requests"),
  PRIVACY_GUARD_BLOCKED(
      "privacy-guard-blocked",
      500,
      "ব্যক্তিগত তথ্য রক্ষায় থামানো হয়েছে",
      "Stopped to protect personal data"),
  INTERNAL_ERROR("internal-error", 500, "অপ্রত্যাশিত ত্রুটি", "Unexpected error"),
  MODEL_OUTPUT_INVALID(
      "model-output-invalid",
      502,
      "বিশ্লেষণ সম্পন্ন করা যায়নি",
      "Analysis could not be completed"),
  MODEL_REFUSED(
      "model-refused", 502, "বিশ্লেষণ সম্পন্ন করা যায়নি", "Analysis could not be completed"),
  LOCAL_MODEL_UNAVAILABLE(
      "local-model-unavailable",
      503,
      "গোপনীয়তা সেবা বন্ধ; কিছুই পাঠানো হয়নি",
      "Privacy service unavailable; nothing was sent"),
  UPSTREAM_UNAVAILABLE(
      "upstream-unavailable",
      503,
      "AI সেবা সাময়িকভাবে বন্ধ",
      "AI service temporarily unavailable"),
  RENDERER_UNAVAILABLE(
      "renderer-unavailable", 503, "মেমো তৈরি করা যায়নি", "Memo could not be rendered"),
  BUDGET_EXCEEDED("budget-exceeded", 503, "খরচের সীমা অতিক্রম", "Analysis exceeded its cost limit"),
  CAPACITY_EXHAUSTED(
      "capacity-exhausted", 503, "আজকের সক্ষমতা শেষ", "Service is at capacity today"),
  INSTANCE_LOST(
      "instance-lost",
      503,
      "বিশ্লেষণ বিঘ্নিত; আবার জমা দিন",
      "Analysis was interrupted; please resubmit"),
  ANALYSIS_TIMEOUT("analysis-timeout", 504, "বিশ্লেষণে বেশি সময় লেগেছে", "Analysis took too long");

  private static final String TYPE_PREFIX = "urn:shromikai:problem:";

  private final String code;
  private final int status;
  private final String titleBn;
  private final String titleEn;

  ProblemType(String code, int status, String titleBn, String titleEn) {
    this.code = code;
    this.status = status;
    this.titleBn = titleBn;
    this.titleEn = titleEn;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }

  public String typeUri() {
    return TYPE_PREFIX + code;
  }

  /** Title in the given language: English for {@code en}, Bangla otherwise (Bangla first). */
  public String title(Locale locale) {
    return "en".equals(locale.getLanguage()) ? titleEn : titleBn;
  }

  /** The problem type for an HTTP status that has no more specific code. */
  public static ProblemType forStatus(int status) {
    return switch (status) {
      case 401 -> INVALID_TOKEN;
      case 403 -> FORBIDDEN;
      case 404 -> NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 413 -> PAYLOAD_TOO_LARGE;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      case 429 -> RATE_LIMITED;
      case 503 -> UPSTREAM_UNAVAILABLE;
      case 504 -> ANALYSIS_TIMEOUT;
      default -> status >= 400 && status < 500 ? VALIDATION_FAILED : INTERNAL_ERROR;
    };
  }
}
