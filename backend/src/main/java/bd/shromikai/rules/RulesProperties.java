package bd.shromikai.rules;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code shromikai.rules.*}: the rule files of the active jurisdiction pack. */
@ConfigurationProperties("shromikai.rules")
public record RulesProperties(List<Path> files) {

  public RulesProperties {
    files = files == null ? List.of() : List.copyOf(files);
  }
}
