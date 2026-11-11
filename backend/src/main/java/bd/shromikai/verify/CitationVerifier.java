package bd.shromikai.verify;

import bd.shromikai.law.LawReadService.SectionLookup;
import bd.shromikai.law.Section;
import bd.shromikai.shared.text.QuoteMatcher;
import bd.shromikai.verify.DecisionTable.Decision;
import bd.shromikai.verify.DecisionTable.SupportInput;
import bd.shromikai.verify.VerificationResult.Checks;
import bd.shromikai.verify.VerificationResult.Existence;
import bd.shromikai.verify.VerificationResult.QuoteMatch;
import bd.shromikai.verify.VerificationResult.Support;
import bd.shromikai.verify.internal.ExistenceCheck;
import bd.shromikai.verify.internal.QuoteCheck;
import bd.shromikai.verify.internal.SupportVerifier;
import bd.shromikai.verify.internal.SupportVerifier.Judged;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;

/**
 * The citation verifier (architecture §7, R-01): three checks in order, short-circuiting, then the
 * decision table. It is the only place a {@link VerifiedCitation} is created, and only for a
 * citation that ends {@code VERIFIED}. Nothing a model wrote reaches a user except through here.
 */
@Service
public class CitationVerifier {

  private final ExistenceCheck existenceCheck;
  private final QuoteCheck quoteCheck;
  private final SupportVerifier support;
  private final VerifyProperties properties;
  private final Clock clock;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Semaphore slots;

  public CitationVerifier(
      bd.shromikai.verify.internal.SectionLookupPort sections,
      SupportVerifier support,
      VerifyProperties properties,
      Clock clock) {
    this.existenceCheck = new ExistenceCheck(sections);
    this.quoteCheck = new QuoteCheck(properties);
    this.support = support;
    this.properties = properties;
    this.clock = clock;
    this.slots = new Semaphore(properties.parallelism());
  }

  /** Verifies one claimed citation. */
  public Verdict verify(ClaimedCitation claim, VerificationContext context) {
    List<ReasonCode> reasons = new ArrayList<>();
    ExistenceCheck.Outcome existence = existenceCheck.check(claim, context);
    if (!existence.passed()) {
      reasons.add(existence.failure());
      return finish(
          claim,
          context,
          DecisionTable.decide(
              CheckOutcome.FAIL, CheckOutcome.SKIPPED, new SupportInput.Skipped(), minConfidence()),
          new Checks(
              new Existence(CheckOutcome.FAIL),
              new QuoteMatch(CheckOutcome.SKIPPED, null, null),
              Support.skipped()),
          reasons,
          null);
    }
    Section section = existence.lookup().section();
    QuoteCheck.Outcome quote = quoteCheck.check(claim.quote(), section);
    if (!quote.passed()) {
      reasons.add(quote.failure());
      return finish(
          claim,
          context,
          DecisionTable.decide(
              CheckOutcome.PASS, CheckOutcome.FAIL, new SupportInput.Skipped(), minConfidence()),
          new Checks(
              new Existence(CheckOutcome.PASS),
              new QuoteMatch(CheckOutcome.FAIL, quote.similarity(), quote.matchedLanguage()),
              Support.skipped()),
          reasons,
          null);
    }
    QuoteMatch quoteResult =
        new QuoteMatch(CheckOutcome.PASS, quote.similarity(), quote.matchedLanguage());
    SupportInput input;
    Support supportResult;
    switch (claim.origin()) {
      case RULE, MAPPING -> {
        input = new SupportInput.Provenance(false);
        supportResult =
            new Support(
                CheckOutcome.PASS,
                null,
                null,
                VerifierIndependence.RULE_PROVENANCE,
                null,
                "reviewed rule source");
      }
      case REVIEWER -> {
        input = new SupportInput.Provenance(true);
        supportResult =
            new Support(
                CheckOutcome.PASS, null, null, VerifierIndependence.HUMAN, null, "human reviewer");
      }
      default -> {
        SupportVerifier.Outcome outcome =
            support.verify(
                claim.claim(),
                section.authoritativeText(),
                section.translation() == null ? null : section.translation().text(),
                claim.quote(),
                context.egress(),
                context.document());
        if (outcome instanceof Judged judged) {
          input = new SupportInput.Judged(judged.label(), judged.confidence());
          boolean pass =
              judged.label() == SupportLabel.SUPPORTS && judged.confidence() >= minConfidence();
          supportResult =
              new Support(
                  pass ? CheckOutcome.PASS : CheckOutcome.FAIL,
                  judged.label(),
                  judged.confidence(),
                  judged.independence(),
                  judged.modelId(),
                  judged.reason());
        } else {
          input = new SupportInput.Unavailable();
          supportResult =
              new Support(CheckOutcome.ERROR, null, null, null, null, "verifier unavailable");
        }
      }
    }
    Decision decision =
        DecisionTable.decide(CheckOutcome.PASS, CheckOutcome.PASS, input, minConfidence());
    reasons.addAll(decision.supportReasons());
    return finish(
        claim,
        context,
        decision,
        new Checks(new Existence(CheckOutcome.PASS), quoteResult, supportResult),
        reasons,
        existence.lookup());
  }

  /** Verifies claims in parallel on virtual threads, at most {@code parallelism} at a time. */
  public List<Verdict> verifyAll(List<ClaimedCitation> claims, VerificationContext context) {
    List<Future<Verdict>> futures = new ArrayList<>();
    for (ClaimedCitation claim : claims) {
      futures.add(
          executor.submit(
              () -> {
                slots.acquire();
                try {
                  return verify(claim, context);
                } finally {
                  slots.release();
                }
              }));
    }
    List<Verdict> verdicts = new ArrayList<>();
    try {
      for (Future<Verdict> future : futures) {
        verdicts.add(future.get());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      futures.forEach(f -> f.cancel(true));
      throw new IllegalStateException("verification interrupted", e);
    } catch (ExecutionException e) {
      futures.forEach(f -> f.cancel(true));
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("verification failed", e.getCause());
    }
    return verdicts;
  }

  private Verdict finish(
      ClaimedCitation claim,
      VerificationContext context,
      Decision decision,
      Checks checks,
      List<ReasonCode> reasons,
      SectionLookup lookup) {
    VerificationResult result =
        new VerificationResult(
            claim.citationId(),
            claim.sectionKey(),
            decision.status(),
            context.attempt(),
            checks,
            reasons,
            clock.instant(),
            context.corpusRelease());
    if (decision.status() != VerificationStatus.VERIFIED) {
      return new Verdict(result, null);
    }
    Section section = lookup.section();
    String stored =
        QuoteMatcher.bestSegment(
            claim.quote(), section.authoritativeText(), properties.quoteSimilarityThreshold());
    return new Verdict(result, new VerifiedCitation(section, stored, result));
  }

  private double minConfidence() {
    return properties.supportMinConfidence();
  }
}
