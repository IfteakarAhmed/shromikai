package bd.shromikai.api.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import bd.shromikai.auth.ApiKeyService;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.trace.AuditEvent;
import bd.shromikai.trace.AuditService;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * FR-65, T-12: bad API keys are always refused with 401, but the audit table gets a bounded number
 * of rows per source address, so a flood of bad keys cannot fill it.
 */
@Tag("unit")
@Requirement("FR-65")
class ApiKeyDeniedAuditThrottleTest {

  private final ApiKeyService keys = mock(ApiKeyService.class);
  private final AuditService audit = mock(AuditService.class);
  private final ProblemResponseWriter problems = mock(ProblemResponseWriter.class);
  private final ApiKeyAuthenticationFilter filter =
      new ApiKeyAuthenticationFilter(keys, audit, problems, new DeniedAuditThrottle(3));

  private int badKeyFrom(String address) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setRemoteAddr(address);
    request.addHeader("Authorization", "Bearer shk_wrong");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response.getStatus();
  }

  @Test
  void writesOnlyThreeAuditRowsForTwentyBadKeysFromOneAddressAndStillAnswers401() throws Exception {
    org.mockito.Mockito.when(keys.authenticate(any())).thenReturn(Optional.empty());
    for (int i = 0; i < 20; i++) {
      badKeyFrom("203.0.113.7");
      verify(problems, times(i + 1)).write(any(), any(), any(), any());
    }

    verify(audit, times(3)).record(any(AuditEvent.class));
  }

  @Test
  void countsAddressesSeparately() throws Exception {
    org.mockito.Mockito.when(keys.authenticate(any())).thenReturn(Optional.empty());
    for (int i = 0; i < 5; i++) {
      badKeyFrom("203.0.113.1");
    }
    verify(audit, times(3)).record(any(AuditEvent.class));

    badKeyFrom("203.0.113.2");

    verify(audit, times(4)).record(any(AuditEvent.class));
  }

  @Test
  void aRequestWithoutACredentialWritesNoRow() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    verify(audit, never()).record(any(AuditEvent.class));
  }
}
