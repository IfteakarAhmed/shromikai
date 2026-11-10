package bd.shromikai;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Every test class carries the right JUnit tag (NFR-15, test-strategy.md §5): {@code *IT} has
 * {@code it}, {@code slow}, {@code live} or {@code eval}; {@code *Test} has {@code unit} and none
 * of those; test methods live only in classes named {@code *Test} or {@code *IT}.
 */
@Tag("unit")
@Requirement("NFR-15")
class TestTaggingRulesTest {

  static final Set<String> INTEGRATION_TAGS = Set.of("it", "slow", "live", "eval");
  static final String SAMPLES = "bd.shromikai.testsupport.tagsamples";

  private static final Set<String> TEST_ANNOTATIONS =
      Set.of(
          "org.junit.jupiter.api.Test",
          "org.junit.jupiter.params.ParameterizedTest",
          "org.junit.jupiter.api.RepeatedTest",
          "org.junit.jupiter.api.TestFactory",
          "org.junit.jupiter.api.TestTemplate");

  @Test
  void shouldTagEveryTestClassCorrectly() {
    List<Class<?>> testClasses =
        importTestClasses().stream()
            .filter(javaClass -> !javaClass.getPackageName().startsWith(SAMPLES))
            .map(JavaClass::reflect)
            .collect(Collectors.toList());

    assertThat(testClasses).hasSizeGreaterThan(10);
    assertThat(violations(testClasses)).isEmpty();
  }

  @Test
  void shouldReportViolations_whenSamplesAreMistagged() {
    List<Class<?>> samples =
        importTestClasses().stream()
            .filter(javaClass -> javaClass.getPackageName().equals(SAMPLES))
            .map(JavaClass::reflect)
            .collect(Collectors.toList());

    assertThat(violations(samples))
        .containsExactlyInAnyOrder(
            SAMPLES + ".UntaggedSampleIT: *IT needs one of [eval, it, live, slow]",
            SAMPLES + ".MistaggedSampleTest: *Test needs tag unit",
            SAMPLES + ".MistaggedSampleTest: *Test must not carry [it]",
            SAMPLES + ".MisnamedSample: test methods outside a *Test or *IT class");
  }

  static List<String> violations(Collection<Class<?>> classes) {
    List<String> violations = new ArrayList<>();
    for (Class<?> type : classes) {
      if (type.isMemberClass() || type.isAnonymousClass() || type.isLocalClass()) {
        continue;
      }
      Set<String> tags =
          AnnotationSupport.findRepeatableAnnotations(type, Tag.class).stream()
              .map(Tag::value)
              .collect(Collectors.toSet());
      String name = type.getSimpleName();
      Set<String> integration =
          tags.stream().filter(INTEGRATION_TAGS::contains).collect(Collectors.toSet());
      if (name.endsWith("IT")) {
        if (integration.isEmpty()) {
          violations.add(
              type.getName() + ": *IT needs one of " + INTEGRATION_TAGS.stream().sorted().toList());
        }
        if (tags.contains("unit")) {
          violations.add(type.getName() + ": *IT must not carry unit");
        }
      } else if (name.endsWith("Test")) {
        if (!tags.contains("unit")) {
          violations.add(type.getName() + ": *Test needs tag unit");
        }
        if (!integration.isEmpty()) {
          violations.add(
              type.getName() + ": *Test must not carry " + integration.stream().sorted().toList());
        }
      } else if (hasTestMethods(type)) {
        violations.add(type.getName() + ": test methods outside a *Test or *IT class");
      }
    }
    return violations;
  }

  private static boolean hasTestMethods(Class<?> type) {
    for (Method method : type.getDeclaredMethods()) {
      for (var annotation : method.getAnnotations()) {
        if (TEST_ANNOTATIONS.contains(annotation.annotationType().getName())) {
          return true;
        }
      }
    }
    return false;
  }

  private static Collection<JavaClass> importTestClasses() {
    return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages("bd.shromikai")
            .stream()
            .filter(javaClass -> !javaClass.isAnnotation())
            .toList();
  }
}
