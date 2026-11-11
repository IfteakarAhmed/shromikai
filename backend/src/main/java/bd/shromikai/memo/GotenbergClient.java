package bd.shromikai.memo;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Renders HTML to PDF with Gotenberg (headless Chromium, FR-55), so Bangla conjuncts are shaped by
 * the same engine on every phone. The HTML is the request body and Gotenberg keeps it on {@code
 * tmpfs} only for the render; nothing is logged or stored here (R-02). Every call has a timeout and
 * at most two retries with exponential backoff on 408, 429, 5xx and connection errors.
 */
@Component
public final class GotenbergClient implements PdfRenderer {

  private static final Logger LOG = LoggerFactory.getLogger(GotenbergClient.class);
  private static final int MAX_RETRIES = 2;

  private final RestClient http;
  private final Duration backoff;

  public GotenbergClient(MemoProperties properties) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(properties.renderTimeout());
    this.http =
        RestClient.builder().baseUrl(properties.gotenbergUrl()).requestFactory(factory).build();
    this.backoff = properties.retryBackoff();
  }

  @Override
  public byte[] pdf(String html) {
    for (int attempt = 0; ; attempt++) {
      try {
        byte[] body = send(html);
        if (body == null || body.length < 5) {
          throw new ProblemException(ProblemType.RENDERER_UNAVAILABLE);
        }
        return body;
      } catch (RestClientResponseException e) {
        int status = e.getStatusCode().value();
        boolean retryable = status == 408 || status == 429 || status >= 500;
        if (!retryable || attempt >= MAX_RETRIES) {
          LOG.warn("memo render failed with status {}", status);
          throw new ProblemException(ProblemType.RENDERER_UNAVAILABLE);
        }
      } catch (org.springframework.web.client.ResourceAccessException e) {
        if (attempt >= MAX_RETRIES) {
          LOG.warn("memo renderer unreachable");
          throw new ProblemException(ProblemType.RENDERER_UNAVAILABLE);
        }
      }
      sleep(backoff.multipliedBy(1L << attempt));
    }
  }

  private byte[] send(String html) {
    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add(
        "files",
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return "index.html";
          }
        });
    form.add("preferCssPageSize", "true");
    form.add("printBackground", "true");
    return http.post()
        .uri("/forms/chromium/convert/html")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .body(byte[].class);
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProblemException(ProblemType.RENDERER_UNAVAILABLE);
    }
  }
}
