package bd.shromikai.law.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A parsed {@code robots.txt} (RFC 9309): the group of our product token, else the {@code *} group;
 * the longest matching rule wins and {@code Allow} beats {@code Disallow} on a tie. A response that
 * is not plain text (the bdlaws portal answers an HTML "404" page with status 200) is not a robots
 * file and imposes no restriction.
 */
final class RobotsPolicy {

  private record Rule(boolean allow, String path) {}

  private static final RobotsPolicy ALLOW_ALL = new RobotsPolicy(List.of());

  private final List<Rule> rules;

  private RobotsPolicy(List<Rule> rules) {
    this.rules = rules;
  }

  static RobotsPolicy allowAll() {
    return ALLOW_ALL;
  }

  /**
   * @param productToken our product token in lower case, for example {@code shromikai-ingest}
   */
  static RobotsPolicy parse(String body, String contentType, String productToken) {
    String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    String trimmed = body.stripLeading();
    if (type.contains("html") || trimmed.startsWith("<")) {
      return ALLOW_ALL;
    }
    List<Rule> specific = new ArrayList<>();
    List<Rule> wildcard = new ArrayList<>();
    boolean sawSpecific = false;
    boolean inSpecific = false;
    boolean inWildcard = false;
    boolean lastWasAgent = false;
    for (String raw : body.split("\\R")) {
      String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
      int colon = line.indexOf(':');
      if (colon < 0) {
        continue;
      }
      String field = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
      String value = line.substring(colon + 1).strip();
      switch (field) {
        case "user-agent" -> {
          if (!lastWasAgent) {
            inSpecific = false;
            inWildcard = false;
          }
          String agent = value.toLowerCase(Locale.ROOT);
          if (agent.equals("*")) {
            inWildcard = true;
          } else if (productToken.startsWith(agent) || agent.startsWith(productToken)) {
            inSpecific = true;
            sawSpecific = true;
          }
          lastWasAgent = true;
        }
        case "allow", "disallow" -> {
          lastWasAgent = false;
          if (!value.isEmpty() || field.equals("allow")) {
            Rule rule = new Rule(field.equals("allow"), value);
            if (inSpecific) {
              specific.add(rule);
            } else if (inWildcard) {
              wildcard.add(rule);
            }
          }
        }
        default -> lastWasAgent = false;
      }
    }
    return new RobotsPolicy(sawSpecific ? specific : wildcard);
  }

  boolean allows(String pathAndQuery) {
    Rule best = null;
    for (Rule rule : rules) {
      if (rule.path().isEmpty() || !matches(rule.path(), pathAndQuery)) {
        continue;
      }
      if (best == null
          || rule.path().length() > best.path().length()
          || (rule.path().length() == best.path().length() && rule.allow())) {
        best = rule;
      }
    }
    return best == null || best.allow();
  }

  private static boolean matches(String pattern, String path) {
    boolean anchored = pattern.endsWith("$");
    String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
    String[] parts = p.split("\\*", -1);
    int pos = 0;
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      if (i == 0) {
        if (!path.startsWith(part)) {
          return false;
        }
        pos = part.length();
      } else {
        int at = path.indexOf(part, pos);
        if (at < 0) {
          return false;
        }
        pos = at + part.length();
      }
    }
    return !anchored || pos == path.length() || p.endsWith("*");
  }
}
