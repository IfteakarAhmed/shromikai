package bd.shromikai.law.internal;

import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.logging.LogKeys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polite HTTP client for official statute sources (FR-12): honours {@code robots.txt}, keeps the
 * configured interval between any two requests, identifies itself with a descriptive User-Agent
 * (contact from configuration), retries 408, 429, 5xx and connection errors at most twice with
 * exponential backoff, decodes pages by byte-order mark (the bdlaws portal serves UTF-16 pages
 * under a UTF-8 header) and caches raw bytes with a SHA-256 sidecar.
 */
final class PoliteFetcher {

  private static final Logger LOG = LoggerFactory.getLogger(PoliteFetcher.class);

  /** Largest response body accepted; a bigger one is refused, never truncated. */
  static final int MAX_BODY_BYTES = 32 * 1024 * 1024;

  private static final int MAX_REDIRECTS = 5;

  /** Slept between attempts and requests; replaced in tests. */
  interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }

  private final ScraperSettings settings;
  private final RawCache cache;
  private final Sleeper sleeper;
  private final Map<String, RobotsPolicy> robots = new HashMap<>();
  private final String productToken;
  private final int maxBodyBytes;
  private final Map<String, Boolean> https = new HashMap<>();
  private long lastRequestNanos = Long.MIN_VALUE;

  PoliteFetcher(ScraperSettings settings, RawCache cache) {
    this(settings, cache, d -> Thread.sleep(d));
  }

  PoliteFetcher(ScraperSettings settings, RawCache cache, Sleeper sleeper) {
    this(settings, cache, sleeper, MAX_BODY_BYTES);
  }

  PoliteFetcher(ScraperSettings settings, RawCache cache, Sleeper sleeper, int maxBodyBytes) {
    this.maxBodyBytes = maxBodyBytes;
    this.settings = settings;
    this.cache = cache;
    this.sleeper = sleeper;
    String agent = settings.userAgent();
    int slash = agent.indexOf('/');
    this.productToken =
        (slash > 0 ? agent.substring(0, slash) : agent.split("\\s+")[0]).toLowerCase(Locale.ROOT);
  }

  /** Fetches {@code url}; reads the cache first only under {@code PREFER_CACHE}. */
  synchronized FetchedPage fetch(String url) {
    if (settings.cachePolicy() == ScraperSettings.CachePolicy.PREFER_CACHE) {
      Optional<byte[]> cached = cache.load(url);
      if (cached.isPresent()) {
        return page(url, cached.get(), Instant.now(), true);
      }
    }
    String transport = preferHttps(url);
    checkRobots(transport);
    byte[] bytes = get(transport, true);
    String sha = Hashing.sha256Hex(bytes);
    cache.store(url, bytes, sha);
    return page(url, bytes, Instant.now(), false);
  }

  /**
   * Uses https for a host that serves it, even if the pack lists an {@code http} URL: one probe of
   * {@code /robots.txt} per host, then the answer is remembered. The cache stays keyed by the URL
   * the caller used.
   */
  private String preferHttps(String url) {
    URI uri = URI.create(url);
    if (!"http".equalsIgnoreCase(uri.getScheme())) {
      return url;
    }
    boolean secure =
        https.computeIfAbsent(
            uri.getAuthority(),
            authority -> {
              try {
                request("https://" + authority + "/robots.txt");
                return true;
              } catch (IOException | FetchException e) {
                return false;
              }
            });
    return secure ? "https" + url.substring(4) : url;
  }

  private FetchedPage page(String url, byte[] bytes, Instant at, boolean fromCache) {
    return new FetchedPage(url, bytes, decode(bytes), Hashing.sha256Hex(bytes), at, fromCache);
  }

  private void checkRobots(String url) {
    URI uri = URI.create(url);
    String origin = uri.getScheme() + "://" + uri.getAuthority();
    RobotsPolicy policy = robots.get(origin);
    if (policy == null) {
      policy = loadRobots(origin);
      robots.put(origin, policy);
    }
    String path =
        (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
            + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    if (!policy.allows(path)) {
      throw new FetchException(
          FetchException.Reason.ROBOTS_DISALLOWED, "robots.txt disallows " + url, 0, null);
    }
  }

  private RobotsPolicy loadRobots(String origin) {
    String robotsUrl = origin + "/robots.txt";
    try {
      Reply response = request(robotsUrl);
      int status = response.status();
      if (status >= 500) {
        throw new FetchException(
            FetchException.Reason.ROBOTS_UNAVAILABLE,
            "robots.txt of " + origin + " answered " + status,
            status,
            null);
      }
      if (status >= 400) {
        return RobotsPolicy.allowAll(); // RFC 9309: no robots file means no restriction
      }
      return RobotsPolicy.parse(
          new String(response.body(), StandardCharsets.UTF_8),
          response.contentType(),
          productToken);
    } catch (IOException e) {
      throw new FetchException(
          FetchException.Reason.ROBOTS_UNAVAILABLE,
          "robots.txt of " + origin + " unreachable",
          0,
          e);
    }
  }

  private byte[] get(String url, boolean retry) {
    int attempts = retry ? settings.maxRetries() + 1 : 1;
    FetchException last = null;
    for (int attempt = 0; attempt < attempts; attempt++) {
      if (attempt > 0) {
        pause(settings.backoffBase().multipliedBy(1L << (attempt - 1)));
      }
      try {
        Reply response = request(url);
        int status = response.status();
        if (status >= 200 && status < 300) {
          return response.body();
        }
        last =
            new FetchException(
                FetchException.Reason.HTTP_ERROR,
                "GET " + url + " answered " + status,
                status,
                null);
        if (!retryable(status)) {
          throw last;
        }
      } catch (IOException e) {
        last =
            new FetchException(FetchException.Reason.UNREACHABLE, "GET " + url + " failed", 0, e);
      }
      LOG.atWarn()
          .addKeyValue(LogKeys.ERROR_CODE, last.reason().name())
          .addKeyValue(LogKeys.REASON_CODE, IngestCliRunner.rootCauseClass(last))
          .addKeyValue(LogKeys.HTTP_STATUS, last.status())
          .log("fetch attempt failed");
    }
    throw last;
  }

  private static boolean retryable(int status) {
    return status == 408 || status == 429 || status >= 500;
  }

  /** Status, content type and the whole body of one response. */
  private record Reply(int status, @Nullable String contentType, byte[] body) {}

  /**
   * One HTTP GET, spaced from the previous one by at least {@code minInterval}. The body is read
   * here: jsoup reads it lazily under the same timeout and reports a slow body as an unchecked
   * exception, which is turned back into an {@link IOException} so it is retried and reported like
   * any other connection failure.
   */
  private Reply request(String url) throws IOException {
    String host = URI.create(url).getHost();
    String current = url;
    try {
      for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
        waitForTurn();
        Connection.Response response;
        try {
          response =
              Jsoup.connect(current)
                  .method(Connection.Method.GET)
                  .userAgent(settings.userAgent())
                  .timeout((int) settings.timeout().toMillis())
                  .ignoreContentType(true)
                  .ignoreHttpErrors(true)
                  .maxBodySize(maxBodyBytes + 1)
                  .followRedirects(false)
                  .execute();
        } finally {
          lastRequestNanos = System.nanoTime();
        }
        int status = response.statusCode();
        String location = response.header("Location");
        if (status >= 300 && status < 400 && location != null) {
          URI next = URI.create(current).resolve(location);
          if (next.getHost() == null || !next.getHost().equalsIgnoreCase(host)) {
            throw new FetchException(
                FetchException.Reason.REDIRECT_OFF_HOST,
                "GET " + current + " redirected to another host",
                status,
                null);
          }
          current = next.toString();
          continue;
        }
        byte[] body = response.bodyAsBytes();
        if (body.length > maxBodyBytes) {
          throw new FetchException(
              FetchException.Reason.BODY_TOO_LARGE,
              "GET " + current + " body exceeds " + maxBodyBytes + " bytes",
              status,
              null);
        }
        return new Reply(status, response.contentType(), body);
      }
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
    throw new FetchException(
        FetchException.Reason.HTTP_ERROR, "GET " + url + " redirected too often", 0, null);
  }

  private void waitForTurn() {
    if (lastRequestNanos == Long.MIN_VALUE) {
      return;
    }
    long waitNanos = settings.minInterval().toNanos() - (System.nanoTime() - lastRequestNanos);
    if (waitNanos > 0) {
      pause(Duration.ofNanos(waitNanos));
    }
  }

  private void pause(Duration duration) {
    try {
      sleeper.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new FetchException(
          FetchException.Reason.UNREACHABLE, "interrupted while waiting", 0, e);
    }
  }

  /** Decodes by byte-order mark first (UTF-16 and UTF-8), UTF-8 otherwise. */
  static String decode(byte[] bytes) {
    Charset charset = StandardCharsets.UTF_8;
    int offset = 0;
    if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
      charset = StandardCharsets.UTF_16BE;
      offset = 2;
    } else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
      charset = StandardCharsets.UTF_16LE;
      offset = 2;
    } else if (bytes.length >= 3
        && (bytes[0] & 0xFF) == 0xEF
        && (bytes[1] & 0xFF) == 0xBB
        && (bytes[2] & 0xFF) == 0xBF) {
      offset = 3;
    }
    return new String(bytes, offset, bytes.length - offset, charset);
  }
}
