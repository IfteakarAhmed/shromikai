package bd.shromikai.api.internal;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Request limits of the REST API under {@code shromikai.api} (api.md §8.1). */
@Validated
@ConfigurationProperties("shromikai.api")
public record ApiProperties(@Min(1) int lawSearchPerMinute) {}
