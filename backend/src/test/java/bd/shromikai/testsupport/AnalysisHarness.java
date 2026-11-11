package bd.shromikai.testsupport;

import static org.mockito.Mockito.mock;

import bd.shromikai.analysis.AnalysisService;
import bd.shromikai.analysis.ClauseClassifier;
import bd.shromikai.analysis.ClauseSegmenter;
import bd.shromikai.analysis.JobStore;
import bd.shromikai.ingest.IntakeProperties;
import bd.shromikai.ingest.IntakeService;
import bd.shromikai.ingest.internal.OcrEngine;
import bd.shromikai.privacy.InjectionDetector;
import bd.shromikai.privacy.LocalEntities;
import bd.shromikai.privacy.LocalEntityExtractor;
import bd.shromikai.privacy.PiiDetector;
import bd.shromikai.privacy.PrivacyEgressGuard;
import bd.shromikai.privacy.PrivacyShield;
import bd.shromikai.privacy.Redactor;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.trace.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;

/** The P5 pipeline wired by hand over the given ports (no Spring context). */
public final class AnalysisHarness {

  public final AuditService audit = mock(AuditService.class);
  public final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  public final PrivacyEgressGuard guard = new PrivacyEgressGuard(new PiiDetector(), audit, meters);
  public final JobStore jobs = new JobStore(Clock.systemUTC());
  public final AnalysisService service;
  public final ModelGateway gateway;

  public AnalysisHarness(ChatPort cloud, ChatPort local, OcrEngine ocr) {
    this.gateway = TestGateways.gateway(cloud, local);
    PiiDetector detector = new PiiDetector();
    PrivacyShield shield =
        new PrivacyShield(
            new LocalEntityExtractor(gateway), new Redactor(detector), new InjectionDetector());
    service =
        new AnalysisService(
            new IntakeService(IntakeProperties.defaults(), ocr),
            shield,
            new ClauseSegmenter(gateway),
            new ClauseClassifier(gateway),
            jobs,
            new bd.shromikai.analysis.AnalysisResults(
                jobs,
                new bd.shromikai.memo.MemoComposer(TestGateways.MAPPER),
                new bd.shromikai.verify.SignedResultCodec(
                    "harness-envelope-key-0123456789abcdefgh"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "k1",
                    java.time.Duration.ofHours(24),
                    Clock.systemUTC()),
                TestGateways.MAPPER,
                Clock.systemUTC(),
                new org.springframework.mock.env.MockEnvironment()),
            Clock.systemUTC());
  }

  /** A stand-in for Ollama's {@code pii-extract}: returns exactly the given entities. */
  public static ChatPort ollamaReturning(LocalEntities entities) {
    return TestGateways.port("ollama", request -> piiAnswer(entities));
  }

  public static JsonNode piiAnswer(LocalEntities e) {
    ObjectNode out = TestGateways.MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE");
    array(out.putArray("persons"), e.persons());
    array(out.putArray("organisations"), e.organisations());
    array(out.putArray("addresses"), e.addresses());
    array(out.putArray("employee_ids"), e.employeeIds());
    return out;
  }

  private static void array(ArrayNode node, List<String> values) {
    values.forEach(node::add);
  }

  /** Entities of a synthetic contract as the local model would find them. */
  public static LocalEntities entitiesOf(String contractId) {
    return new LocalEntities(
        SyntheticContracts.values(contractId, "PERSON"),
        SyntheticContracts.values(contractId, "ORG"),
        SyntheticContracts.values(contractId, "ADDRESS"),
        SyntheticContracts.values(contractId, "EMPLOYEE_ID"));
  }
}
