package bd.shromikai.testsupport.tagsamples;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Negative sample for TestTaggingRulesTest: test methods in a class that no Gradle task selects by
 * name. The {@code sample} tag keeps every task from running it.
 */
@Tag("sample")
public class MisnamedSample {

  @Test
  void neverRuns() {}
}
