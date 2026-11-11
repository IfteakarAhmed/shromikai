package bd.shromikai.memo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code shromikai.memo.*} for the renderer. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemoProperties.class)
public class MemoConfiguration {}
