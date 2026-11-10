package bd.shromikai.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** One pgvector container per test JVM, shared by every Spring context. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

  /** Same image and tag as docker-compose.yml. */
  public static final DockerImageName IMAGE =
      DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres");

  private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(IMAGE);

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return CONTAINER;
  }
}
