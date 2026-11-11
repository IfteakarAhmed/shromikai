package bd.shromikai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

/** FR-48: the shipped application.yml prices bind to {@link PricingProperties} (C29). */
@Tag("unit")
@Requirement("FR-48")
class PricingBindingTest {

  @Test
  void shouldBindThePricesOfApplicationYml() throws IOException {
    MutablePropertySources sources = new MutablePropertySources();
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"))
        .forEach(sources::addLast);

    PricingProperties pricing =
        new Binder(
                ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources))
            .bind("shromikai", PricingProperties.class)
            .get();

    assertThat(pricing.prices()).containsKeys("claude-sonnet-5-5", "claude-haiku-5-5", "gemini");
    assertThat(pricing.priceFor("claude-haiku-5-5").longContext()).isNotNull();
    assertThat(pricing.priceFor("claude-sonnet-5-5").output()).isGreaterThan(0);
  }
}
