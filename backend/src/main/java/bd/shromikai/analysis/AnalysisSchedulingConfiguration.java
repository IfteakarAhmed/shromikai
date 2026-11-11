package bd.shromikai.analysis;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the job store's expiry sweep. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AnalysisSchedulingConfiguration {}
