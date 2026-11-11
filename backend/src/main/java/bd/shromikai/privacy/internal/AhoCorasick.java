package bd.shromikai.privacy.internal;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/** Multi-pattern substring search, used to look for every protected value in one pass. */
public final class AhoCorasick {

  private static final class Node {
    final Map<Character, Node> next = new HashMap<>();
    Node fail;
    boolean terminal;
  }

  private final Node root = new Node();
  private final boolean empty;

  public AhoCorasick(Collection<String> patterns) {
    boolean any = false;
    for (String pattern : patterns) {
      if (pattern == null || pattern.isEmpty()) {
        continue;
      }
      any = true;
      Node node = root;
      for (char c : pattern.toCharArray()) {
        node = node.next.computeIfAbsent(c, k -> new Node());
      }
      node.terminal = true;
    }
    this.empty = !any;
    Queue<Node> queue = new ArrayDeque<>();
    for (Node child : root.next.values()) {
      child.fail = root;
      queue.add(child);
    }
    while (!queue.isEmpty()) {
      Node node = queue.remove();
      for (var entry : node.next.entrySet()) {
        Node child = entry.getValue();
        Node f = node.fail;
        while (f != root && !f.next.containsKey(entry.getKey())) {
          f = f.fail;
        }
        child.fail =
            f.next.containsKey(entry.getKey()) && f.next.get(entry.getKey()) != child
                ? f.next.get(entry.getKey())
                : root;
        child.terminal |= child.fail.terminal;
        queue.add(child);
      }
    }
  }

  /** {@code true} if any pattern occurs in the text. */
  public boolean matches(String text) {
    if (empty) {
      return false;
    }
    Node node = root;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      while (node != root && !node.next.containsKey(c)) {
        node = node.fail;
      }
      node = node.next.getOrDefault(c, root);
      if (node.terminal) {
        return true;
      }
    }
    return false;
  }
}
