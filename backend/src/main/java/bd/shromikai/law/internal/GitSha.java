package bd.shromikai.law.internal;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Short git SHA of the working tree, stamped on generated reports (R-07). */
final class GitSha {

  private GitSha() {}

  static String current(Path directory) {
    String env = System.getenv("GIT_SHA");
    if (env != null && !env.isBlank()) {
      return env.strip();
    }
    Path existing = directory;
    while (existing != null && !java.nio.file.Files.isDirectory(existing)) {
      existing = existing.getParent();
    }
    if (existing == null) {
      return "unknown";
    }
    try {
      Process process =
          new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
              .directory(existing.toFile())
              .redirectErrorStream(true)
              .start();
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return "unknown";
      }
      String out = new String(process.getInputStream().readAllBytes()).strip();
      return process.exitValue() == 0 && out.matches("[0-9a-f]{7,40}") ? out : "unknown";
    } catch (java.io.IOException e) {
      return "unknown";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "unknown";
    }
  }
}
