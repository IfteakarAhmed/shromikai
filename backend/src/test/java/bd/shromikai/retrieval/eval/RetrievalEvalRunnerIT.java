package bd.shromikai.retrieval.eval;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.law.CorpusStatsService;
import bd.shromikai.law.LawReadService;
import bd.shromikai.retrieval.RetrievalService;
import bd.shromikai.retrieval.eval.RetrievalEvalRunner.Question;
import bd.shromikai.retrieval.eval.RetrievalEvalRunner.Row;
import bd.shromikai.shared.ai.EgressGuard;
import bd.shromikai.shared.ai.EmbeddingPort;
import bd.shromikai.shared.ai.PromptRegistry;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-23: runs the five-row retrieval ablation over {@code eval/retrieval/questions.jsonl} against
 * the real corpus with real bge-m3 embeddings (local Ollama) and writes {@code
 * eval/reports/retrieval.{md,json}}. Needs a populated development database; start it with {@code
 * ./gradlew evalRetrieval}, which loads {@code ../.env}. The rewrite row calls Claude Haiku only
 * when {@code ANTHROPIC_API_KEY} is set, otherwise it is reported as not run.
 *
 * <p>The questions are synthetic and derived from the statute text, never user contract text, so
 * the test lets {@code query-rewrite} calls through the egress guard; production keeps the
 * fail-closed guard until the privacy shield exists.
 */
@Tag("eval")
@Requirement("FR-23")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("eval")
class RetrievalEvalRunnerIT {

  @TestConfiguration
  static class AllowEvalRewrite {
    @Bean
    @Primary
    EgressGuard evalEgressGuard() {
      return request -> {
        if (!"query-rewrite".equals(request.promptId())) {
          throw new bd.shromikai.shared.ai.EgressBlockedException("eval-only-allows-query-rewrite");
        }
      };
    }
  }

  @Autowired RetrievalService retrieval;
  @Autowired EmbeddingPort embeddings;
  @Autowired PromptRegistry prompts;
  @Autowired ShromikaiProperties properties;
  @Autowired LawReadService laws;
  @Autowired CorpusStatsService stats;

  @Test
  void shouldProduceTheAblationTable() throws IOException, InterruptedException {
    Path root = Path.of("..").toAbsolutePath().normalize();
    List<Question> questions =
        RetrievalEvalRunner.load(root.resolve("eval/retrieval/questions.jsonl"));
    assertThat(questions.size()).as("FR-23 needs at least 30 questions").isGreaterThanOrEqualTo(30);
    assertThat(questions.stream().filter(q -> q.language().equals("bn")).count()).isPositive();
    assertThat(questions.stream().filter(q -> q.language().equals("en")).count()).isPositive();
    assertThat(stats.current().embeddedSections()).as("run ./gradlew embedLaws first").isPositive();

    String key = System.getenv("ANTHROPIC_API_KEY");
    String skipped =
        key == null || key.isBlank()
            ? "ANTHROPIC_API_KEY is not set, so no Claude Haiku call was made; the row is not simulated."
            : null;

    RetrievalEvalRunner runner = new RetrievalEvalRunner(retrieval);
    List<Row> rows = runner.run(questions, skipped);
    var context =
        new RetrievalEvalRunner.Context(
            gitSha(root),
            Instant.now().toString(),
            String.valueOf(laws.corpusRelease()),
            embeddings.modelId(),
            properties.models().fast(),
            "query-rewrite v" + prompts.latest("query-rewrite").version(),
            (int) stats.current().embeddedSections(),
            skipped);
    runner.write(root.resolve("eval/reports"), context, questions, rows);

    assertThat(rows).hasSize(5);
    assertThat(rows.get(2).all().questions()).isEqualTo(questions.size());
    assertThat(root.resolve("eval/reports/retrieval.md")).exists();
  }

  private static String gitSha(Path root) throws IOException, InterruptedException {
    Process p =
        new ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(root.toFile()).start();
    String sha = new String(p.getInputStream().readAllBytes()).trim();
    p.waitFor(10, TimeUnit.SECONDS);
    return sha.isEmpty() ? "unknown" : sha;
  }
}
