package bd.shromikai.shared.logging;

import java.util.Set;

/**
 * The only keys a log line may carry (architecture §15). Use these constants for MDC entries and
 * SLF4J key-value pairs; {@code LogAllowlistTest} fails on any other key in {@code src/main}.
 */
public final class LogKeys {

  public static final String TIMESTAMP = "timestamp";
  public static final String LEVEL = "level";
  public static final String LOGGER = "logger";
  public static final String MESSAGE = "message";
  public static final String REQUEST_ID = "requestId";
  public static final String ANALYSIS_ID = "analysisId";
  public static final String STAGE = "stage";
  public static final String PROMPT_ID = "promptId";
  public static final String PROMPT_VERSION = "promptVersion";
  public static final String MODEL_ID = "modelId";
  public static final String DURATION_MS = "durationMs";
  public static final String TOKENS_IN = "tokensIn";
  public static final String TOKENS_OUT = "tokensOut";
  public static final String COST_USD_MICROS = "costUsdMicros";
  public static final String STATUS = "status";
  public static final String ERROR_CODE = "errorCode";
  public static final String REASON_CODE = "reasonCode";
  public static final String HTTP_METHOD = "httpMethod";
  public static final String HTTP_PATH = "httpPath";
  public static final String HTTP_STATUS = "httpStatus";
  public static final String SUBJECT_KIND = "subjectKind";
  public static final String ORG_ID = "orgId";
  public static final String INSTANCE_ID = "instanceId";

  /** Every allowlisted key. */
  public static final Set<String> ALL =
      Set.of(
          TIMESTAMP,
          LEVEL,
          LOGGER,
          MESSAGE,
          REQUEST_ID,
          ANALYSIS_ID,
          STAGE,
          PROMPT_ID,
          PROMPT_VERSION,
          MODEL_ID,
          DURATION_MS,
          TOKENS_IN,
          TOKENS_OUT,
          COST_USD_MICROS,
          STATUS,
          ERROR_CODE,
          REASON_CODE,
          HTTP_METHOD,
          HTTP_PATH,
          HTTP_STATUS,
          SUBJECT_KIND,
          ORG_ID,
          INSTANCE_ID);

  /** Keys written by the formatter itself; never accepted from MDC or key-value pairs. */
  static final Set<String> RESERVED = Set.of(TIMESTAMP, LEVEL, LOGGER, MESSAGE);

  private LogKeys() {}
}
