package bd.shromikai.law.internal;

import bd.shromikai.law.IloMappingLoader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads the ILO mapping at startup so that a reviewed row whose quote is missing from the corpus
 * stops the application, like the rule loader (FR-16, architecture §7.8). Before the first corpus
 * release there is nothing to check against.
 */
@Component
@Order(0)
final class IloMappingStartupCheck implements ApplicationRunner {

  private final LawRepository repository;
  private final IngestProperties properties;

  IloMappingStartupCheck(LawRepository repository, IngestProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    IloMappingLoader.load(
        properties.mappingFile(), repository::authoritativeText, repository.hasRelease());
  }
}
