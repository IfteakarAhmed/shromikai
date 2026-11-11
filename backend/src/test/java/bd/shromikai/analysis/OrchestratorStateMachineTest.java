package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bd.shromikai.rules.Severity;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.ModelTimeoutException;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-48: the explicit state machine after CLASSIFY (architecture §5.1): happy path, step timeout,
 * budget, a verifier that is down, and a failure in each state with its problem code. Stand-in
 * model answers; the pipeline, verifier, rule engine and agents are the real classes.
 */
@Tag("unit")
@Requirement("FR-48")
class OrchestratorStateMachineTest {

  private static List<Stage> stages(AnalysisJob job) {
    return job.trace().stream().map(TraceEvent::stage).toList();
  }

  @Test
  void shouldRunTheStatesInOrderOnTheHappyPath_andSkipCorrectWhenEverythingIsVerified() {
    PipelineHarness h = PipelineHarness.standard();

    AnalysisJob job = h.run();

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.ANALYSED);
    assertThat(job.failure()).isNull();
    assertThat(stages(job))
        .containsExactly(
            Stage.RETRIEVE, Stage.RULES, Stage.RISK, Stage.VERIFY, Stage.COMPOSE, Stage.COMPLETE);
    assertThat(job.trace()).allSatisfy(e -> assertThat(e.outcome()).isEqualTo(TraceEvent.OK));
    AnalysisJob.Outcome outcome = job.outcome();
    assertThat(outcome.findings()).hasSize(1);
    Finding finding = outcome.findings().getFirst();
    // The rule finding (7 days < 14) and the LLM finding on the same section merged (FR-49).
    assertThat(finding.source()).isEqualTo(FindingSource.RULE_ENGINE);
    assertThat(finding.verification()).isEqualTo(FindingVerification.VERIFIED);
    assertThat(finding.citations()).hasSize(1);
    assertThat(finding.id()).isEqualTo("f1");
    assertThat(finding.confidence()).isEqualTo(0.95, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(outcome.redlines())
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.id()).isEqualTo("r1");
              assertThat(r.findingId()).isEqualTo("f1");
            });
    assertThat(finding.redlineId()).isEqualTo("r1");
    assertThat(outcome.verifierIndependence()).isNotNull();
    assertThat(h.reviewStore.items).isEmpty();
    verify(h.runs)
        .succeeded(
            any(), anyInt(), anyInt(), anyInt(), any(), any(), anyLong(), anyLong(), anyLong(),
            anyLong(), any());
    verify(h.traces, atLeastOnce())
        .append(
            eq(job.id()),
            eq("COMPLETE"),
            any(),
            anyLong(),
            eq("OK"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @Test
  void shouldWriteOneContentFreeTraceEventPerTransition() {
    PipelineHarness h = PipelineHarness.standard();

    AnalysisJob job = h.run();

    for (TraceEvent event : job.trace()) {
      String detail = event.detail().toString();
      assertThat(detail).doesNotContain(PipelineHarness.CLAUSE_1).doesNotContain("seven days");
    }
    TraceEvent risk =
        job.trace().stream().filter(e -> e.stage() == Stage.RISK).findFirst().orElseThrow();
    assertThat(risk.promptId()).isEqualTo("risk-analysis");
    assertThat(risk.promptVersion()).as("risk-analysis v2 (ADR-0016)").isEqualTo(2);
    assertThat(risk.tokensIn()).isPositive();
    assertThat(risk.costUsdMicros()).isPositive();
    assertThat(risk.detail().get("claimedSectionKeys").toString())
        .contains(AnalysisFixtures.KEY_NOTICE);
  }

  @Test
  void shouldRunRetrieveAndRulesInParallel() {
    CountDownLatch factsAsked = new CountDownLatch(1);
    AtomicBoolean retrievalSawFacts = new AtomicBoolean();
    AnalysisFixtures.StubSource waiting =
        new AnalysisFixtures.StubSource(
            q -> {
              try {
                // Sequential execution would never see the clause-facts call while retrieving.
                retrievalSawFacts.set(factsAsked.await(5, TimeUnit.SECONDS));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return List.of(AnalysisFixtures.KEY_NOTICE);
            });
    PipelineHarness h =
        new PipelineHarness(
            request -> {
              if (request.promptId().equals("clause-facts")) {
                factsAsked.countDown();
              }
              return null;
            },
            waiting,
            List.of(AnalysisFixtures.noticeMinRule()),
            PipelineHarness.properties());

    AnalysisJob job = h.run();

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.ANALYSED);
    assertThat(retrievalSawFacts).isTrue();
  }

  @Test
  void shouldFailWithAnalysisTimeoutOnTheSlowStep_andReleaseTheJobsMemory() {
    AnalysisProperties fast =
        new AnalysisProperties(
            new AnalysisProperties.Analysis(
                Duration.ofSeconds(30),
                Map.of("risk", Duration.ofMillis(200)),
                java.util.Set.of("XV")),
            new AnalysisProperties.Budget(1.0, 1_000_000));
    PipelineHarness h =
        new PipelineHarness(
            request -> {
              if (request.promptId().equals("risk-analysis")) {
                try {
                  Thread.sleep(3000);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
              return null;
            },
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(AnalysisFixtures.noticeMinRule()),
            fast);

    AnalysisJob job = h.run();

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.FAILED);
    assertThat(job.failure().type()).isEqualTo(ProblemType.ANALYSIS_TIMEOUT);
    TraceEvent last = job.trace().getLast();
    assertThat(last.stage()).isEqualTo(Stage.RISK);
    assertThat(last.outcome()).isEqualTo(TraceEvent.FAILED);
    assertThat(job.clauses()).isEmpty();
    assertThat(job.placeholders().size()).isZero();
    verify(h.runs).failed(any(), eq("analysis-timeout"), anyLong(), anyLong(), any());
    verify(h.runs, never())
        .succeeded(
            any(), anyInt(), anyInt(), anyInt(), any(), any(), anyLong(), anyLong(), anyLong(),
            anyLong(), any());
  }

  @Test
  void shouldFailWithBudgetExceededWhenTheJobSpendsMoreThanItsBudget() {
    AnalysisProperties tiny =
        new AnalysisProperties(
            PipelineHarness.properties().analysis(),
            new AnalysisProperties.Budget(0.0001, 1_000_000));
    PipelineHarness h =
        new PipelineHarness(
            null,
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(AnalysisFixtures.noticeMinRule()),
            tiny);

    AnalysisJob job = h.run();

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.FAILED);
    assertThat(job.failure().type()).isEqualTo(ProblemType.BUDGET_EXCEEDED);
    assertThat(job.clauses()).isEmpty();
    verify(h.runs).failed(any(), eq("budget-exceeded"), anyLong(), anyLong(), any());
  }

  @Test
  void shouldFailWithBudgetExceededWhenTheTokenBudgetIsSpent() {
    AnalysisProperties fewTokens =
        new AnalysisProperties(
            PipelineHarness.properties().analysis(), new AnalysisProperties.Budget(1.0, 500));

    PipelineHarness h =
        new PipelineHarness(
            null,
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(),
            fewTokens);

    assertThat(h.run().failure().type()).isEqualTo(ProblemType.BUDGET_EXCEEDED);
  }

  @Test
  void shouldDegradeToUncertain_neverToVerified_whenTheVerifierIsDown() {
    PipelineHarness h =
        new PipelineHarness(
            request -> {
              if (request.promptId().startsWith("verify-support")) {
                throw new UpstreamUnavailableException("verifier-down", 503, null);
              }
              return null;
            },
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(),
            PipelineHarness.properties());

    AnalysisJob job = h.run();

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.ANALYSED);
    Finding finding = job.outcome().findings().getFirst();
    assertThat(finding.verification()).isEqualTo(FindingVerification.UNCERTAIN);
    assertThat(finding.citations()).isEmpty();
    assertThat(finding.reasonCodes()).contains("VERIFIER_UNAVAILABLE");
    assertThat(job.outcome().redlines()).as("an UNCERTAIN finding gets no counter-offer").isEmpty();
    assertThat(h.calls("counter-offer")).isZero();
    assertThat(h.reviewStore.items).hasSize(1);
    assertThat(stages(job)).contains(Stage.CORRECT);
  }

  @Test
  void shouldLeaveTheFindingUncertain_whenTheRepairCallTimesOut() {
    PipelineHarness h =
        new PipelineHarness(
            request -> {
              if (request.promptId().equals("risk-citation-repair")) {
                throw new ModelTimeoutException("repair-timeout");
              }
              return unverifiableRiskAnswer(request);
            },
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(),
            PipelineHarness.properties());

    AnalysisJob job = h.run();

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.ANALYSED);
    assertThat(h.calls("risk-citation-repair")).isEqualTo(1);
    Finding finding = job.outcome().findings().getFirst();
    assertThat(finding.verification()).isEqualTo(FindingVerification.UNCERTAIN);
    assertThat(finding.citations()).isEmpty();
    assertThat(h.reviewStore.items).hasSize(1);
  }

  @Test
  void shouldDropTheRedlineWithAWarning_whenTheCounterOfferCallIsUnavailable() {
    PipelineHarness h =
        new PipelineHarness(
            request -> {
              if (request.promptId().equals("counter-offer")) {
                throw new UpstreamUnavailableException("anthropic-http-503", 503, null);
              }
              return null;
            },
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(AnalysisFixtures.noticeMinRule()),
            PipelineHarness.properties());

    AnalysisJob job = h.run();

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.ANALYSED);
    assertThat(job.outcome().findings().getFirst().verification())
        .isEqualTo(FindingVerification.VERIFIED);
    assertThat(job.outcome().redlines()).isEmpty();
    assertThat(job.outcome().warnings()).anyMatch(w -> w.code().equals("counter-offer-dropped"));
  }

  @Test
  void shouldWarnThatRedlinesWereUnchecked_whenNoRuleCouldCheckTheProposal() {
    PipelineHarness h =
        new PipelineHarness(
            null,
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(),
            PipelineHarness.properties());

    AnalysisJob job = h.run();

    assertThat(job.outcome().redlines()).isNotEmpty();
    assertThat(job.outcome().warnings())
        .filteredOn(w -> w.code().equals("counter-offer-unchecked"))
        .as("one warning, not one per redline")
        .hasSize(1);
  }

  @Test
  void shouldNotWarnThatRedlinesWereUnchecked_whenARuleCheckedThem() {
    AnalysisJob job = PipelineHarness.standard().run();

    assertThat(job.outcome().redlines()).isNotEmpty();
    assertThat(job.outcome().warnings()).noneMatch(w -> w.code().equals("counter-offer-unchecked"));
  }

  private static com.fasterxml.jackson.databind.JsonNode unverifiableRiskAnswer(
      bd.shromikai.shared.ai.ChatRequest request) {
    return request.promptId().equals("risk-analysis")
        ? TestGateways.json(
            """
            {"status":"OK","reason":"NONE","findings":[{"title":"Dismissal on short notice",
             "kind":"CONFLICT","severity":"HIGH","non_obvious":false,
             "clause_type":"NOTICE_AND_RESIGNATION","clause_ids":["c1"],
             "explanation":"The clause gives only seven days notice of dismissal.",
             "citations":[{"section_key":"XV-LAB:9@original",
               "quote":"every worker is entitled to a company car after one year of service",
               "claim":"A worker is entitled to a company car."}],
             "confidence":0.8}]}
            """)
        : null;
  }

  @Test
  void shouldRunCorrectOnlyWhenACitationIsNotVerified_andLeaveOneReviewItem() {
    PipelineHarness h =
        new PipelineHarness(
            request ->
                request.promptId().equals("risk-analysis")
                    ? TestGateways.json(
                        """
                        {"status":"OK","reason":"NONE","findings":[{"title":"Dismissal on short notice",
                         "kind":"CONFLICT","severity":"HIGH","non_obvious":false,
                         "clause_type":"NOTICE_AND_RESIGNATION","clause_ids":["c1"],
                         "explanation":"The clause gives only seven days notice of dismissal.",
                         "citations":[{"section_key":"XV-LAB:9@original",
                           "quote":"every worker is entitled to a company car after one year of service",
                           "claim":"A worker is entitled to a company car."}],
                         "confidence":0.8}]}
                        """)
                    : null,
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(),
            PipelineHarness.properties());

    AnalysisJob job = h.run();

    assertThat(stages(job)).containsSubsequence(Stage.VERIFY, Stage.CORRECT, Stage.COMPOSE);
    assertThat(h.calls("risk-citation-repair")).isEqualTo(1);
    Finding finding = job.outcome().findings().getFirst();
    assertThat(finding.verification()).isEqualTo(FindingVerification.UNCERTAIN);
    assertThat(finding.citations()).isEmpty();
    assertThat(h.reviewStore.items).hasSize(1);
    assertThat(finding.reviewItemId()).isEqualTo(h.reviewStore.items.getFirst().id());
    assertThat(job.outcome().reviewItems()).isEqualTo(1);
  }

  /** A failure in each state reaches FAILED with that state's problem code, and frees memory. */
  @Test
  void shouldReachFailedWithTheStatesProblemCode() {
    record Case(String name, PipelineHarness harness, Stage stage, ProblemType expected) {}
    var okSource = new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE));
    List<Case> cases =
        List.of(
            new Case(
                "retrieval breaks",
                new PipelineHarness(
                    null,
                    new AnalysisFixtures.StubSource(
                        q -> {
                          throw new IllegalStateException("database down");
                        }),
                    List.of(),
                    PipelineHarness.properties()),
                Stage.RETRIEVE,
                ProblemType.INTERNAL_ERROR),
            new Case(
                "clause-facts provider outage",
                new PipelineHarness(
                    r -> {
                      if (r.promptId().equals("clause-facts")) {
                        throw new UpstreamUnavailableException("anthropic-http-503", 503, null);
                      }
                      return null;
                    },
                    okSource,
                    List.of(),
                    PipelineHarness.properties()),
                Stage.RULES,
                ProblemType.UPSTREAM_UNAVAILABLE),
            new Case(
                "risk-analysis returns invalid output",
                new PipelineHarness(
                    r -> {
                      if (r.promptId().equals("risk-analysis")) {
                        throw new ModelOutputInvalidException("schema-invalid-after-repair");
                      }
                      return null;
                    },
                    okSource,
                    List.of(),
                    PipelineHarness.properties()),
                Stage.RISK,
                ProblemType.MODEL_OUTPUT_INVALID),
            new Case(
                "egress guard blocks the risk call",
                new PipelineHarness(
                    r -> {
                      if (r.promptId().equals("risk-analysis")) {
                        throw new EgressBlockedException("pii_detected");
                      }
                      return null;
                    },
                    okSource,
                    List.of(),
                    PipelineHarness.properties()),
                Stage.RISK,
                ProblemType.PRIVACY_GUARD_BLOCKED),
            new Case(
                "counter-offer egress block (a policy failure still stops the job; an outage only drops the redline)",
                new PipelineHarness(
                    r -> {
                      if (r.promptId().equals("counter-offer")) {
                        throw new EgressBlockedException("pii_detected");
                      }
                      return null;
                    },
                    okSource,
                    List.of(),
                    PipelineHarness.properties()),
                Stage.COMPOSE,
                ProblemType.PRIVACY_GUARD_BLOCKED));

    for (Case c : cases) {
      AnalysisJob job = c.harness().run();

      assertThat(job.status()).as(c.name()).isEqualTo(AnalysisJob.Status.FAILED);
      assertThat(job.failure().type()).as(c.name()).isEqualTo(c.expected());
      assertThat(job.trace().getLast().stage()).as(c.name()).isEqualTo(c.stage());
      assertThat(job.trace().getLast().outcome()).as(c.name()).isEqualTo(TraceEvent.FAILED);
      assertThat(job.clauses()).as(c.name()).isEmpty();
      assertThat(job.placeholders().size()).as(c.name()).isZero();
      assertThat(job.outcome()).as(c.name()).isNull();
    }
  }

  @Test
  void shouldNotContinueAJobThatHasNotFinishedClassify() {
    PipelineHarness h = PipelineHarness.standard();
    AnalysisJob job = h.jobs.create();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> h.pipeline.run(job, AnalysisRequest.of(Side.WORKER), AnalysisSubject.dev()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldNeverUseSeverityFromTheModelOverTheRulesWhenTheyDisagree() {
    // The rule says HIGH (fixture); the stand-in model says CRITICAL for the same section.
    PipelineHarness h =
        new PipelineHarness(
            request ->
                request.promptId().equals("risk-analysis")
                    ? TestGateways.json(
                        PipelineHarness.defaultAnswer(request)
                            .toString()
                            .replace("\"HIGH\"", "\"CRITICAL\""))
                    : null,
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(AnalysisFixtures.noticeMinRule()),
            PipelineHarness.properties());

    AnalysisJob job = h.run();

    Finding finding = job.outcome().findings().getFirst();
    assertThat(finding.severity()).isEqualTo(Severity.HIGH);
    assertThat(h.reviewStore.items)
        .singleElement()
        .satisfies(item -> assertThat(item.reasonCodes()).contains("RULE_LLM_CONFLICT"));
  }
}
