package bd.shromikai.rules;

import bd.shromikai.law.LawReadService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the rule files when the application starts: a bean that cannot be created stops startup,
 * which is how an invalid active rule fails fast (architecture §9.1, {@code
 * RuleLoaderFailFastTest}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RulesProperties.class)
public class RulesConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(RulesConfiguration.class);

  @Bean
  RuleSet ruleSet(RulesProperties properties, LawReadService law) {
    boolean release = law.corpusRelease() != null;
    RuleLoader.CorpusLookup corpus =
        (act, section) ->
            law.section(act, section, null)
                .filter(s -> s.isCurrent())
                .map(s -> new RuleLoader.CurrentSection(s.sectionKey(), s.authoritativeText()));
    List<Rule> rules = new ArrayList<>();
    List<String> problems = new ArrayList<>();
    for (var file : properties.files()) {
      RuleLoader.Result result = RuleLoader.load(file, corpus, release);
      rules.addAll(result.rules());
      problems.addAll(result.draftProblems());
    }
    RuleSet set = new RuleSet(rules, problems);
    if (!release && !set.active().isEmpty()) {
      // Active rules are verified against the corpus (R-10); without a release nothing was.
      throw new RuleLoadException(
          set.active().size()
              + " rule(s) are active but no corpus release exists, so their quotes were not"
              + " verified; ingest the statutes first or keep the rules in draft");
    }
    LOG.info(
        "rules loaded: {} active, {} draft, {} draft problems",
        set.active().size(),
        set.draftCount(),
        problems.size());
    return set;
  }
}
