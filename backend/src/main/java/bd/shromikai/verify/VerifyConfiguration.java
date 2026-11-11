package bd.shromikai.verify;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code shromikai.verify.*} (architecture §7.7). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VerifyProperties.class)
public class VerifyConfiguration {}
