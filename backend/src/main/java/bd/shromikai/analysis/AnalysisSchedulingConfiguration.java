package bd.shromikai.analysis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the job store's expiry sweep and binds the pipeline's timing and budget settings. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AnalysisProperties.class)
class AnalysisSchedulingConfiguration {}
