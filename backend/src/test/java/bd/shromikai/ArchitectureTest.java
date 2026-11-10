package bd.shromikai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Module map and extra rules of AGENTS.md §4 (NFR-15). */
@Tag("unit")
@Requirement("NFR-15")
class ArchitectureTest {

  private static final String ROOT = "bd.shromikai";

  /** AGENTS.md §4: row may depend on the listed modules (besides itself). */
  static final Map<String, Set<String>> ALLOWED = new LinkedHashMap<>();

  static {
    ALLOWED.put("shared", Set.of());
    ALLOWED.put("trace", Set.of("shared"));
    ALLOWED.put("auth", Set.of("shared", "trace"));
    ALLOWED.put("law", Set.of("shared", "trace"));
    ALLOWED.put("retrieval", Set.of("shared", "trace", "law"));
    ALLOWED.put("ingest", Set.of("shared", "trace"));
    ALLOWED.put("privacy", Set.of("shared", "trace"));
    ALLOWED.put("rules", Set.of("shared", "trace", "law"));
    ALLOWED.put("verify", Set.of("shared", "trace", "law"));
    ALLOWED.put("memo", Set.of("shared", "trace", "law", "rules", "verify"));
    ALLOWED.put(
        "analysis",
        Set.of(
            "shared", "trace", "law", "retrieval", "ingest", "privacy", "rules", "verify", "memo"));
    ALLOWED.put(
        "api",
        Set.of(
            "shared", "trace", "auth", "law", "retrieval", "rules", "verify", "memo", "analysis"));
    ALLOWED.put(
        "mcp",
        Set.of("shared", "trace", "auth", "law", "retrieval", "rules", "verify", "analysis"));
    ALLOWED.put(
        "eval",
        Set.of(
            "shared",
            "trace",
            "auth",
            "law",
            "retrieval",
            "ingest",
            "privacy",
            "rules",
            "verify",
            "memo",
            "analysis"));
  }

  private static JavaClasses classes;

  @BeforeAll
  static void importMainClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(ROOT);
    assertThat(classes).isNotEmpty();
  }

  private static String pkg(String module) {
    return ROOT + "." + module + "..";
  }

  @Test
  void shouldPlaceEveryClassInAKnownModule() {
    String[] packages =
        Stream.concat(Stream.of(ROOT), ALLOWED.keySet().stream().map(ArchitectureTest::pkg))
            .toArray(String[]::new);

    classes().should().resideInAnyPackage(packages).check(classes);
  }

  @TestFactory
  Stream<DynamicTest> shouldRespectTheModuleDependencyMatrix() {
    return ALLOWED.entrySet().stream()
        .map(
            entry ->
                DynamicTest.dynamicTest(
                    entry.getKey(),
                    () -> {
                      String[] forbidden =
                          ALLOWED.keySet().stream()
                              .filter(other -> !other.equals(entry.getKey()))
                              .filter(other -> !entry.getValue().contains(other))
                              .map(ArchitectureTest::pkg)
                              .toArray(String[]::new);
                      noClasses()
                          .that()
                          .resideInAPackage(pkg(entry.getKey()))
                          .should()
                          .dependOnClassesThat()
                          .resideInAnyPackage(forbidden)
                          .allowEmptyShould(true)
                          .check(classes);
                    }));
  }

  @Test
  void shouldHaveNoCyclesBetweenModules() {
    slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(classes);
  }

  @TestFactory
  Stream<DynamicTest> rule1_shouldKeepInternalPackagesPrivate() {
    return ALLOWED.keySet().stream()
        .map(
            module ->
                DynamicTest.dynamicTest(
                    module,
                    () ->
                        noClasses()
                            .that()
                            .resideOutsideOfPackage(pkg(module))
                            .should()
                            .dependOnClassesThat()
                            .resideInAPackage(ROOT + "." + module + ".internal..")
                            .allowEmptyShould(true)
                            .check(classes)));
  }

  @Test
  void rule2_shouldConstructVerifiedCitationOnlyInVerify() {
    noClasses()
        .that()
        .resideOutsideOfPackage(pkg("verify"))
        .should()
        .callConstructorWhere(
            com.tngtech.archunit.base.DescribedPredicate.describe(
                "target is VerifiedCitation",
                call -> call.getTargetOwner().getSimpleName().equals("VerifiedCitation")))
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void rule3_shouldConfineModelSdksToSharedAi() {
    noClasses()
        .that()
        .resideOutsideOfPackage(ROOT + ".shared.ai..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.anthropic..", "org.springframework.ai..")
        .check(classes);
  }

  @Test
  void rule4_shouldReadPromptsOnlyInPromptRegistry() throws IOException {
    List<String> offenders = new ArrayList<>();
    Path main = Path.of("src/main/java");
    try (Stream<Path> files = Files.walk(main)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        if (file.getFileName().toString().equals("PromptRegistry.java")) {
          continue;
        }
        if (Files.readString(file).matches("(?s).*\"(classpath:)?/?prompts/.*")) {
          offenders.add(main.relativize(file).toString());
        }
      }
    }
    assertThat(offenders).as("only PromptRegistry reads prompts/").isEmpty();
  }

  @Test
  void rule5_shouldNotUseStandardStreamsOrPrintStackTrace() {
    noClasses()
        .should()
        .accessField(System.class, "out")
        .orShould()
        .accessField(System.class, "err")
        .orShould()
        .callMethod(Throwable.class, "printStackTrace")
        .check(classes);
  }

  @Test
  void rule6_shouldDeclareControllersAndMcpToolsOnlyInApiAndMcp() {
    noClasses()
        .that()
        .resideOutsideOfPackages(pkg("api"), pkg("mcp"))
        .should()
        .beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
        .orShould()
        .beAnnotatedWith("org.springframework.stereotype.Controller")
        .orShould()
        .beAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice")
        .orShould()
        .beAnnotatedWith("org.springframework.web.bind.annotation.ControllerAdvice")
        .check(classes);
    noClasses()
        .that()
        .resideOutsideOfPackage(pkg("mcp"))
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springaicommunity.mcp..", "io.modelcontextprotocol..")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("org.springframework.ai.tool.annotation.Tool")
        .check(classes);
  }

  @Test
  void rule7_shouldNotDependOnEval() {
    noClasses()
        .that()
        .resideOutsideOfPackage(pkg("eval"))
        .should()
        .dependOnClassesThat()
        .resideInAPackage(pkg("eval"))
        .check(classes);
  }
}
