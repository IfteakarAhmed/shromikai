package bd.shromikai.testsupport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * No internet in tests (test-strategy.md §5). When {@code shromikai.test.blockExternalHosts} is
 * {@code true}, installs a default {@link ProxySelector} that routes every non-local host to a
 * closed local port, so the connection fails and the host is recorded. Auto-registered through
 * {@code META-INF/services}; the {@code live} task switches it off.
 */
public final class BlockExternalHostsExtension implements BeforeAllCallback {

  public static final String PROPERTY = "shromikai.test.blockExternalHosts";

  /** Port 9 (discard) is closed on CI runners and developer machines. */
  static final InetSocketAddress BLACK_HOLE = new InetSocketAddress("127.0.0.1", 9);

  private static final Set<String> LOCAL_HOSTS =
      Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0.0.0.0");

  private static final List<String> BLOCKED = new CopyOnWriteArrayList<>();
  private static volatile boolean installed;

  @Override
  public void beforeAll(ExtensionContext context) {
    install();
  }

  static synchronized void install() {
    if (installed || !Boolean.getBoolean(PROPERTY)) {
      return;
    }
    ProxySelector.setDefault(new BlockingProxySelector());
    installed = true;
  }

  /** Hosts that a test tried to reach and was refused. */
  public static List<String> blockedHosts() {
    return List.copyOf(BLOCKED);
  }

  static boolean isLocal(String host) {
    String lower = host.toLowerCase(Locale.ROOT);
    return LOCAL_HOSTS.contains(lower) || lower.startsWith("127.") || lower.endsWith(".localhost");
  }

  static final class BlockingProxySelector extends ProxySelector {

    @Override
    public List<Proxy> select(URI uri) {
      String host = uri.getHost();
      if (host == null || isLocal(host)) {
        return List.of(Proxy.NO_PROXY);
      }
      BLOCKED.add(host);
      return List.of(new Proxy(Proxy.Type.HTTP, BLACK_HOLE));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException e) {
      // Expected for blocked hosts.
    }
  }
}
