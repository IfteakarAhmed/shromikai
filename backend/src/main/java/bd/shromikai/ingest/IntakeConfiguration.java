package bd.shromikai.ingest;

import bd.shromikai.ingest.internal.OcrEngine;
import bd.shromikai.ingest.internal.TesseractCliEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires intake: limits from {@code shromikai.intake} and the local Tesseract engine. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IntakeProperties.class)
public class IntakeConfiguration {

  @Bean
  @ConditionalOnMissingBean(OcrEngine.class)
  OcrEngine ocrEngine(IntakeProperties props) {
    return new TesseractCliEngine(props.tesseractCommand(), props.ocrTimeout());
  }
}
