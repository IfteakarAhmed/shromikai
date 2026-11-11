package bd.shromikai.law.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Decides what a parsed section does to the versions already stored for the same act and section
 * (FR-13, FR-15, ADR-0011 decision 4). Only one version is current; an older version is never
 * rewritten or reconstructed, it is only demoted.
 */
final class VersionPlanner {

  record Existing(long id, String versionLabel, String contentHash, boolean current) {}

  record Incoming(String baseLabel, String contentHash, boolean current) {}

  enum Action {
    /** The same text under the same label is already stored in the same state. */
    NOOP,
    /** Store a new version row. */
    INSERT,
    /** The text of a stored non-current version is the source's text again: make it current. */
    REACTIVATE
  }

  /**
   * @param label label of the row to insert (meaningful for {@code INSERT})
   * @param reactivateId row to make current (for {@code REACTIVATE})
   * @param demote ids of rows that stop being current
   * @param amendedFrom previously current row that the new version amends ({@code AMENDED_BY} edge
   *     from it to the new row), or {@code null}
   */
  record Plan(
      Action action,
      String label,
      @Nullable Long reactivateId,
      List<Long> demote,
      @Nullable Long amendedFrom) {}

  private VersionPlanner() {}

  static Plan plan(List<Existing> existing, Incoming incoming) {
    List<Existing> sameLine =
        existing.stream()
            .filter(e -> baseOf(e.versionLabel()).equals(incoming.baseLabel()))
            .toList();
    for (Existing row : sameLine) {
      if (row.contentHash().equals(incoming.contentHash())) {
        if (row.current() == incoming.current() || row.current()) {
          return new Plan(Action.NOOP, row.versionLabel(), null, List.of(), null);
        }
        List<Long> demote = currentIds(existing, row.id());
        return new Plan(Action.REACTIVATE, row.versionLabel(), row.id(), demote, null);
      }
    }
    String label = incoming.baseLabel();
    if (!sameLine.isEmpty()) {
      int revision =
          sameLine.stream().mapToInt(e -> revisionOf(e.versionLabel())).max().orElse(1) + 1;
      label = incoming.baseLabel() + "-r" + revision;
    }
    Long amendedFrom =
        existing.stream()
            .filter(Existing::current)
            .max(Comparator.comparingLong(Existing::id))
            .map(Existing::id)
            .orElse(null);
    return new Plan(Action.INSERT, label, null, currentIds(existing, -1), amendedFrom);
  }

  private static List<Long> currentIds(List<Existing> existing, long except) {
    List<Long> ids = new ArrayList<>();
    for (Existing row : existing) {
      if (row.current() && row.id() != except) {
        ids.add(row.id());
      }
    }
    return ids;
  }

  /** {@code amend-2026-r2} to {@code amend-2026}. */
  static String baseOf(String label) {
    int at = label.lastIndexOf("-r");
    if (at > 0 && label.substring(at + 2).matches("[0-9]+")) {
      return label.substring(0, at);
    }
    return label;
  }

  private static int revisionOf(String label) {
    String base = baseOf(label);
    return base.equals(label) ? 1 : Integer.parseInt(label.substring(base.length() + 2));
  }
}
