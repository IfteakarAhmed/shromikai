package bd.shromikai.analysis;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs an analysis in the background on a virtual thread (FR-58): INTAKE to CLASSIFY, then the
 * pipeline. The job exists, owned by its subject, before the caller gets an answer, so progress and
 * result can be asked for at once. The caller may wait a limited time ({@code sync-wait}) and
 * answer {@code 200} if the analysis finished, else {@code 202}. Concurrency per organisation and
 * per subject is limited (NFR-20); guest limits live in {@code GuestQuotaService}.
 */
@Service
public class AnalysisRunner {

  /** Concurrent analyses of one organisation (NFR-20). */
  public static final int ORGANISATION_CONCURRENCY = 5;

  /** Concurrent analyses of one signed-in user without an organisation. */
  public static final int USER_CONCURRENCY = 2;

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisRunner.class);

  private final AnalysisService service;
  private final AnalysisPipeline pipeline;
  private final BaselineAgent baselines;
  private final SampleContract samples;
  private final JobStore jobs;
  private final AnalysisProperties properties;
  private final List<AnalysisFinishedListener> listeners;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Map<UUID, CompletableFuture<Void>> running = new ConcurrentHashMap<>();

  public AnalysisRunner(
      AnalysisService service,
      AnalysisPipeline pipeline,
      BaselineAgent baselines,
      SampleContract samples,
      JobStore jobs,
      AnalysisProperties properties,
      List<AnalysisFinishedListener> listeners) {
    this.service = service;
    this.pipeline = pipeline;
    this.baselines = baselines;
    this.samples = samples;
    this.jobs = jobs;
    this.properties = properties;
    this.listeners = List.copyOf(listeners);
  }

  /**
   * Starts an analysis of pasted text.
   *
   * @param onFinished called once when the job has ended in any way, for example to release a quota
   *     slot
   */
  public AnalysisJob startText(
      AnalysisSubject subject, AnalysisRequest request, String text, Runnable onFinished) {
    return start(subject, request, job -> service.executeText(job, text), onFinished);
  }

  /** Starts an analysis of the bundled synthetic sample contract (api.md §4.1, FR-69). */
  public AnalysisJob startSample(
      AnalysisSubject subject, AnalysisRequest request, Runnable onFinished) {
    return start(
        subject,
        request,
        job -> {
          job.markSample();
          service.executeText(job, samples.text());
        },
        onFinished);
  }

  /** Compare mode on the bundled sample. */
  public AnalysisJob compareSample(
      AnalysisSubject subject, AnalysisRequest request, Runnable onFinished) {
    return start(
        subject,
        request,
        job -> {
          job.markSample();
          service.executeText(job, samples.text());
        },
        onFinished,
        baselines::run,
        true);
  }

  /** Starts an analysis of uploaded files. */
  public AnalysisJob startFiles(
      AnalysisSubject subject, AnalysisRequest request, List<byte[]> files, Runnable onFinished) {
    return start(subject, request, job -> service.executeFiles(job, files), onFinished);
  }

  /** Compare mode (FR-59): the analysis, then the plain-LLM baseline on the same redacted text. */
  public AnalysisJob compareText(
      AnalysisSubject subject, AnalysisRequest request, String text, Runnable onFinished) {
    return start(
        subject, request, job -> service.executeText(job, text), onFinished, baselines::run, true);
  }

  public AnalysisJob compareFiles(
      AnalysisSubject subject, AnalysisRequest request, List<byte[]> files, Runnable onFinished) {
    return start(
        subject,
        request,
        job -> service.executeFiles(job, files),
        onFinished,
        baselines::run,
        true);
  }

  private AnalysisJob start(
      AnalysisSubject subject,
      AnalysisRequest request,
      java.util.function.Consumer<AnalysisJob> intake,
      Runnable onFinished) {
    return start(subject, request, intake, onFinished, job -> {}, false);
  }

  private AnalysisJob start(
      AnalysisSubject subject,
      AnalysisRequest request,
      java.util.function.Consumer<AnalysisJob> intake,
      Runnable onFinished,
      java.util.function.Consumer<AnalysisJob> afterAnalysis,
      boolean compare) {
    checkConcurrency(subject);
    AnalysisJob job = service.open(subject, request);
    CompletableFuture<Void> future =
        CompletableFuture.runAsync(
            () -> {
              try {
                intake.accept(job);
                if (job.status() == AnalysisJob.Status.CLASSIFIED) {
                  pipeline.run(job, request, subject);
                }
                afterAnalysis.accept(job);
              } catch (RuntimeException unexpected) {
                LOG.error(
                    "analysis failed unexpectedly: {}", unexpected.getClass().getSimpleName());
                if (job.completedAt() == null) {
                  job.fail(new ProblemException(ProblemType.INTERNAL_ERROR));
                }
              } finally {
                running.remove(job.id());
                notifyListeners(job, compare);
                onFinished.run();
              }
            },
            executor);
    running.put(job.id(), future);
    return job;
  }

  private void notifyListeners(AnalysisJob job, boolean compare) {
    for (AnalysisFinishedListener listener : listeners) {
      try {
        listener.finished(job, compare);
      } catch (RuntimeException e) {
        LOG.warn("analysis listener failed: {}", e.getClass().getSimpleName());
      }
    }
  }

  /** Waits until the job ends or {@code sync-wait} has passed; true when it has ended. */
  public boolean awaitFinished(AnalysisJob job) {
    return awaitFinished(job, properties.analysis().syncWait());
  }

  public boolean awaitFinished(AnalysisJob job, Duration limit) {
    CompletableFuture<Void> future = running.get(job.id());
    if (future == null) {
      return job.completedAt() != null;
    }
    try {
      future.get(limit.toMillis(), TimeUnit.MILLISECONDS);
      return true;
    } catch (TimeoutException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (java.util.concurrent.ExecutionException e) {
      return true;
    }
  }

  private void checkConcurrency(AnalysisSubject subject) {
    if (subject.orgId() != null) {
      long inOrg = jobs.countRunning(j -> subject.orgId().equals(ownerOrg(j)));
      if (inOrg >= ORGANISATION_CONCURRENCY) {
        throw new ProblemException(ProblemType.CONCURRENCY_LIMIT, null, 5);
      }
    } else if ("USER".equals(subject.kind())) {
      long own = jobs.countRunning(j -> subject.key().equals(ownerKey(j)));
      if (own >= USER_CONCURRENCY) {
        throw new ProblemException(ProblemType.CONCURRENCY_LIMIT, null, 5);
      }
    }
  }

  private static UUID ownerOrg(AnalysisJob job) {
    return job.owner() == null ? null : job.owner().orgId();
  }

  private static String ownerKey(AnalysisJob job) {
    return job.owner() == null ? "" : job.owner().key();
  }
}
