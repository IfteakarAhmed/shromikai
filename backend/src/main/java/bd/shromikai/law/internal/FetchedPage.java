package bd.shromikai.law.internal;

import java.time.Instant;

/** One fetched page: raw bytes (for the checksum and the cache), decoded text and when. */
record FetchedPage(
    String url, byte[] bytes, String text, String sha256, Instant fetchedAt, boolean fromCache) {}
