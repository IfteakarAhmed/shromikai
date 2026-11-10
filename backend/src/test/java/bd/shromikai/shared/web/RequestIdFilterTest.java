package bd.shromikai.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
@Requirement("FR-03")
class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void shouldEchoRequestId_whenClientSendsOne() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/meta");
    request.addHeader("X-Request-Id", "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123");
    assertThat(RequestIdFilter.currentOf(request)).isEqualTo("abc-123");
  }

  @Test
  void shouldGenerateRequestId_whenHeaderIsMissing() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(new MockHttpServletRequest("GET", "/"), response, (req, res) -> {});

    String generated = response.getHeader("X-Request-Id");
    assertThat(UUID.fromString(generated)).isNotNull();
  }

  @Test
  void shouldReplaceRequestId_whenHeaderIsMalformed() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
    request.addHeader("X-Request-Id", "bad id\nwith newline");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader("X-Request-Id")).isNotEqualTo("bad id\nwith newline");
    assertThat(UUID.fromString(response.getHeader("X-Request-Id"))).isNotNull();
  }

  @Test
  void shouldExposeRequestIdInMdcDuringRequestOnly() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
    request.addHeader("X-Request-Id", "mdc-1");
    AtomicReference<String> seen = new AtomicReference<>();
    FilterChain chain = (req, res) -> seen.set(MDC.get("requestId"));

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(seen).hasValue("mdc-1");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void shouldGenerateRequestId_whenFilterDidNotRun() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    String first = RequestIdFilter.currentOf(request);

    assertThat(RequestIdFilter.currentOf(request)).isEqualTo(first);
  }

  @Test
  void shouldReuseRequestId_whenErrorDispatchFollowsTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/meta");
    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    String original = RequestIdFilter.currentOf(request);
    request.setDispatcherType(DispatcherType.ERROR);
    MockHttpServletResponse errorResponse = new MockHttpServletResponse();
    AtomicReference<String> seen = new AtomicReference<>();

    filter.doFilter(request, errorResponse, (req, res) -> seen.set(MDC.get("requestId")));

    assertThat(errorResponse.getHeader("X-Request-Id")).isEqualTo(original);
    assertThat(RequestIdFilter.currentOf(request)).isEqualTo(original);
    assertThat(seen).hasValue(original);
  }
}
