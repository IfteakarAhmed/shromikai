package bd.shromikai.auth.internal;

import bd.shromikai.shared.Hashing;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Makes and fingerprints API keys (FR-65): {@code shk_} followed by 40 base62 characters from a
 * {@link SecureRandom}. Only {@link #hash} and {@link #prefix} are stored; the key itself is
 * returned once and never kept.
 */
public final class ApiKeyGenerator {

  public static final String PREFIX = "shk_";
  private static final String ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final int BODY_LENGTH = 40;
  private static final Pattern FORMAT = Pattern.compile("shk_[0-9A-Za-z]{40}");
  private static final SecureRandom RANDOM = new SecureRandom();

  private ApiKeyGenerator() {}

  public static String generate() {
    StringBuilder key = new StringBuilder(PREFIX.length() + BODY_LENGTH).append(PREFIX);
    for (int i = 0; i < BODY_LENGTH; i++) {
      key.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return key.toString();
  }

  /** SHA-256 of the whole key, lower-case hex. */
  public static String hash(String key) {
    return Hashing.sha256Hex(key);
  }

  /** The first eight characters, enough for an admin to tell keys apart. */
  public static String prefix(String key) {
    return key.substring(0, 8);
  }

  /** True when the text has the shape of a key; a JWT never does. */
  public static boolean looksLikeKey(String candidate) {
    return FORMAT.matcher(candidate).matches();
  }
}
