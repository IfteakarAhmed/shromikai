package bd.shromikai.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("NFR-15")
class BlockExternalHostsExtensionTest {

  private final BlockExternalHostsExtension.BlockingProxySelector selector =
      new BlockExternalHostsExtension.BlockingProxySelector();

  @Test
  void shouldConnectDirectly_whenHostIsLocal() {
    assertThat(selector.select(URI.create("http://localhost:8080/x")))
        .containsExactly(Proxy.NO_PROXY);
    assertThat(selector.select(URI.create("http://127.0.0.1:1/x"))).containsExactly(Proxy.NO_PROXY);
  }

  @Test
  void shouldRouteToClosedPort_whenHostIsExternal() {
    var proxies = selector.select(URI.create("https://api.anthropic.com/v1/messages"));

    assertThat(proxies).hasSize(1);
    assertThat(proxies.get(0).address()).isEqualTo(BlockExternalHostsExtension.BLACK_HOLE);
    assertThat(BlockExternalHostsExtension.blockedHosts()).contains("api.anthropic.com");
  }

  @Test
  void shouldFailRealConnection_whenBlockingIsActiveInThisJvm() {
    assertThat(Boolean.getBoolean(BlockExternalHostsExtension.PROPERTY))
        .as("the unit test task runs with external hosts blocked")
        .isTrue();
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://example.org/")).build();

    assertThatThrownBy(() -> client.send(request, HttpResponse.BodyHandlers.discarding()))
        .isInstanceOf(IOException.class);
  }
}
