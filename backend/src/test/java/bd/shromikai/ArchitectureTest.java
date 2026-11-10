package bd.shromikai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
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

/**
 * Module map and extra rules of AGENTS.md §4 (NFR-15). Every rule is built for a root package, so
 * the same rule runs against the main code (must pass) and against the deliberately violating
 * samples in {@value #SAMPLES} (must fail).
 */
@Tag("unit")
@Requirement("NFR-15")
class ArchitectureTest {

  private static final String ROOT = "bd.shromikai";
  static final String SAMPLES = "bd.shromikai.testsupport.archsamples";

  /** Rule 3: model SDK packages that only {@code shared.ai} may use. */
  static final String[] MODEL_SDK_PACKAGES = {
    "com.anthropic..",
    "org.springframework.ai.chat..",
    "org.springframework.ai.embedding..",
    "org.springframework.ai.model..",
    "org.springframework.ai.ollama..",
  };

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
  private static JavaClasses samples;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(ROOT);
    samples =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(SAMPLES);
    assertThat(classes).isNotEmpty();
    assertThat(samples).isNotEmpty();
  }

  private static String pkg(String root, String module) {
    return root + "." + module + "..";
  }

  // ------------------------------------------------------------------------------------------
  // Rules, built per root package
  // ------------------------------------------------------------------------------------------

  static ArchRule everyClassInAKnownModule(String root) {
    String[] packages =
        Stream.concat(Stream.of(root), ALLOWED.keySet().stream().map(module -> pkg(root, module)))
            .toArray(String[]::new);
    return classes().that().resideInAPackage(root + "..").should().resideInAnyPackage(packages);
  }

  static ArchRule dependencyMatrix(String root, String module) {
    String[] forbidden =
        ALLOWED.keySet().stream()
            .filter(other -> !other.equals(module))
            .filter(other -> !ALLOWED.get(module).contains(other))
            .map(other -> pkg(root, other))
            .toArray(String[]::new);
    return noClasses()
        .that()
        .resideInAPackage(pkg(root, module))
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(forbidden)
        .allowEmptyShould(true);
  }

  static ArchRule noCycles(String root) {
    return slices().matching(root + ".(*)..").should().beFreeOfCycles();
  }

  static ArchRule rule1InternalPackagesPrivate(String root, String module) {
    return noClasses()
        .that()
        .resideOutsideOfPackage(pkg(root, module))
        .should()
        .dependOnClassesThat()
        .resideInAPackage(root + "." + module + ".internal..")
        .allowEmptyShould(true);
  }

  static ArchRule rule2VerifiedCitationOnlyInVerify(String root) {
    return noClasses()
        .that()
        .resideOutsideOfPackage(pkg(root, "verify"))
        .should()
        .callConstructorWhere(
            DescribedPredicate.describe(
                "target is VerifiedCitation",
                call -> call.getTargetOwner().getSimpleName().equals("VerifiedCitation")))
        .allowEmptyShould(true);
  }

  /**
   * Model SDKs (Anthropic, Spring AI chat, embedding, model and Ollama) stay in {@code shared.ai}.
   * Spring AI MCP and tool types are not model SDKs: module mcp uses them (P8, rule 6).
   */
  static ArchRule rule3ModelSdksOnlyInSharedAi(String root) {
    return noClasses()
        .that()
        .resideInAPackage(root + "..")
        .and()
        .resideOutsideOfPackage(root + ".shared.ai..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(MODEL_SDK_PACKAGES)
        .allowEmptyShould(true);
  }

  /** Rule 4 is a source scan: no string literal naming {@code prompts/} outside PromptRegistry. */
  static List<String> rule4PromptReaders(Path sources) throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.walk(sources)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        if (file.getFileName().toString().equals("PromptRegistry.java")) {
          continue;
        }
        if (Files.readString(file).matches("(?s).*\"(classpath:)?/?prompts/.*")) {
          offenders.add(sources.relativize(file).toString().replace('\\', '/'));
        }
      }
    }
    return offenders;
  }

  static ArchRule rule5NoStandardStreams(String root) {
    return noClasses()
        .that()
        .resideInAPackage(root + "..")
        .should()
        .accessField(System.class, "out")
        .orShould()
        .accessField(System.class, "err")
        .orShould()
        .callMethod(Throwable.class, "printStackTrace");
  }

  static ArchRule rule6ControllersOnlyInApiAndMcp(String root) {
    return noClasses()
        .that()
        .resideInAPackage(root + "..")
        .and()
        .resideOutsideOfPackages(pkg(root, "api"), pkg(root, "mcp"))
        .should()
        .beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
        .orShould()
        .beAnnotatedWith("org.springframework.stereotype.Controller")
        .orShould()
        .beAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice")
        .orShould()
        .beAnnotatedWith("org.springframework.web.bind.annotation.ControllerAdvice")
        .allowEmptyShould(true);
  }

  static ArchRule rule6McpToolsOnlyInMcp(String root) {
    return noClasses()
        .that()
        .resideInAPackage(root + "..")
        .and()
        .resideOutsideOfPackage(pkg(root, "mcp"))
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springaicommunity.mcp..", "io.modelcontextprotocol..")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("org.springframework.ai.tool.annotation.Tool")
        .allowEmptyShould(true);
  }

  static ArchRule rule7NothingDependsOnEval(String root) {
    return noClasses()
        .that()
        .resideInAPackage(root + "..")
        .and()
        .resideOutsideOfPackage(pkg(root, "eval"))
        .should()
        .dependOnClassesThat()
        .resideInAPackage(pkg(root, "eval"))
        .allowEmptyShould(true);
  }

  // ------------------------------------------------------------------------------------------
  // Main code: every rule holds
  // ------------------------------------------------------------------------------------------

  @Test
  void shouldPlaceEveryClassInAKnownModule() {
    everyClassInAKnownModule(ROOT).check(classes);
  }

  @TestFactory
  Stream<DynamicTest> shouldRespectTheModuleDependencyMatrix() {
    return ALLOWED.keySet().stream()
        .map(
            module ->
                DynamicTest.dynamicTest(
                    module, () -> dependencyMatrix(ROOT, module).check(classes)));
  }

  @Test
  void shouldHaveNoCyclesBetweenModules() {
    noCycles(ROOT).check(classes);
  }

  @TestFactory
  Stream<DynamicTest> rule1_shouldKeepInternalPackagesPrivate() {
    return ALLOWED.keySet().stream()
        .map(
            module ->
                DynamicTest.dynamicTest(
                    module, () -> rule1InternalPackagesPrivate(ROOT, module).check(classes)));
  }

  @Test
  void rule2_shouldConstructVerifiedCitationOnlyInVerify() {
    rule2VerifiedCitationOnlyInVerify(ROOT).check(classes);
  }

  @Test
  void rule3_shouldConfineModelSdksToSharedAi() {
    rule3ModelSdksOnlyInSharedAi(ROOT).check(classes);
  }

  @Test
  void rule4_shouldReadPromptsOnlyInPromptRegistry() throws IOException {
    assertThat(rule4PromptReaders(Path.of("src/main/java")))
        .as("only PromptRegistry reads prompts/")
        .isEmpty();
  }

  @Test
  void rule5_shouldNotUseStandardStreamsOrPrintStackTrace() {
    rule5NoStandardStreams(ROOT).check(classes);
  }

  @Test
  void rule6_shouldDeclareControllersAndMcpToolsOnlyInApiAndMcp() {
    rule6ControllersOnlyInApiAndMcp(ROOT).check(classes);
    rule6McpToolsOnlyInMcp(ROOT).check(classes);
  }

  @Test
  void rule7_shouldNotDependOnEval() {
    rule7NothingDependsOnEval(ROOT).check(classes);
  }

  // ------------------------------------------------------------------------------------------
  // Samples: every rule reports its violating sample, so no rule passes vacuously
  // ------------------------------------------------------------------------------------------

  private static void assertReports(ArchRule rule, String sampleClass) {
    EvaluationResult result = rule.evaluate(samples);
    assertThat(result.hasViolation()).as(rule.getDescription()).isTrue();
    assertThat(String.join("\n", result.getFailureReport().getDetails()))
        .contains(SAMPLES + "." + sampleClass);
  }

  @Test
  void shouldReportSample_whenClassIsOutsideEveryModule() {
    assertReports(everyClassInAKnownModule(SAMPLES), "stray.StrayClass");
  }

  @Test
  void shouldReportSample_whenModuleBreaksTheMatrix() {
    assertReports(dependencyMatrix(SAMPLES, "shared"), "shared.SharedUsesLaw");
  }

  @Test
  void shouldReportSample_whenModulesFormACycle() {
    assertReports(noCycles(SAMPLES), "shared.SharedUsesLaw");
  }

  @Test
  void shouldReportSample_whenInternalPackageIsUsedFromOutside() {
    assertReports(rule1InternalPackagesPrivate(SAMPLES, "law"), "api.UsesLawInternal");
  }

  @Test
  void shouldReportSample_whenVerifiedCitationIsBuiltOutsideVerify() {
    assertReports(rule2VerifiedCitationOnlyInVerify(SAMPLES), "api.ForgesCitation");
  }

  @Test
  void shouldReportSample_whenModelSdkIsUsedOutsideSharedAi() {
    assertReports(rule3ModelSdksOnlyInSharedAi(SAMPLES), "analysis.UsesSpringAiChat");
  }

  @Test
  void shouldAllowMcp_toUseSpringAiMcpAndToolTypes() {
    JavaClasses mcp =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(SAMPLES + ".mcp");
    assertThat(mcp.get(SAMPLES + ".mcp.McpTools").getDirectDependenciesFromSelf())
        .extracting(dependency -> dependency.getTargetClass().getPackageName())
        .contains("org.springframework.ai.mcp", "org.springframework.ai.tool.annotation");

    assertThat(rule3ModelSdksOnlyInSharedAi(SAMPLES).evaluate(mcp).hasViolation()).isFalse();
    assertThat(rule6McpToolsOnlyInMcp(SAMPLES).evaluate(mcp).hasViolation()).isFalse();
    assertThat(dependencyMatrix(SAMPLES, "mcp").evaluate(mcp).hasViolation()).isFalse();
  }

  @Test
  void shouldReportSample_whenPromptIsReadOutsidePromptRegistry() throws IOException {
    assertThat(rule4PromptReaders(Path.of("src/test/java/" + SAMPLES.replace('.', '/'))))
        .containsExactly("analysis/ReadsPrompts.java");
  }

  @Test
  void shouldReportSample_whenStandardStreamIsUsed() {
    assertReports(rule5NoStandardStreams(SAMPLES), "trace.PrintsToStdout");
  }

  @Test
  void shouldReportSample_whenControllerIsOutsideApiAndMcp() {
    assertReports(rule6ControllersOnlyInApiAndMcp(SAMPLES), "analysis.StrayController");
  }

  @Test
  void shouldReportSample_whenMcpToolIsOutsideMcp() {
    assertReports(rule6McpToolsOnlyInMcp(SAMPLES), "analysis.ToolOutsideMcp");
  }

  @Test
  void shouldReportSample_whenSomethingDependsOnEval() {
    assertReports(rule7NothingDependsOnEval(SAMPLES), "law.UsesEval");
  }
}
