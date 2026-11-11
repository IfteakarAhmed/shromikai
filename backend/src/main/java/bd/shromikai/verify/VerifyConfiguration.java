package bd.shromikai.verify;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code shromikai.verify.*} and the envelope settings (architecture §7.7). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({VerifyProperties.class, EnvelopeProperties.class})
public class VerifyConfiguration {}
