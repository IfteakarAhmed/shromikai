package bd.shromikai.law.internal;

import bd.shromikai.shared.Hashing;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Raw pages under {@code data/raw/} (git-ignored) with a SHA-256 sidecar {@code *.sha256} (FR-12).
 * Pages are statute text and carry no personal data.
 */
final class RawCache {

  private final Path root;

  RawCache(Path root) {
    this.root = root;
  }

  Path pathFor(String url) {
    URI uri = URI.create(url);
    String host = uri.getHost() == null ? "local" : uri.getHost();
    String name =
        (uri.getPath() == null ? "" : uri.getPath())
            + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
    String safe = name.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^_+", "");
    if (safe.isEmpty()) {
      safe = "index";
    }
    return root.resolve(host)
        .resolve(safe.endsWith(".html") || safe.endsWith(".pdf") ? safe : safe + ".html");
  }

  void store(String url, byte[] bytes, String sha256) {
    Path file = pathFor(url);
    try {
      Files.createDirectories(file.getParent());
      Files.write(file, bytes);
      Files.writeString(sidecar(file), sha256 + "\n", StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The cached bytes, only when the sidecar checksum still matches them. */
  Optional<byte[]> load(String url) {
    return loadFile(pathFor(url));
  }

  Optional<byte[]> loadFile(Path file) {
    try {
      if (!Files.isRegularFile(file) || !Files.isRegularFile(sidecar(file))) {
        return Optional.empty();
      }
      byte[] bytes = Files.readAllBytes(file);
      String expected = Files.readString(sidecar(file), StandardCharsets.UTF_8).strip();
      return Hashing.sha256Hex(bytes).equals(expected) ? Optional.of(bytes) : Optional.empty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Path sidecar(Path file) {
    return file.resolveSibling(file.getFileName() + ".sha256");
  }

  Path root() {
    return root;
  }
}
