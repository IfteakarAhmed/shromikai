package bd.shromikai.tooling;

import bd.shromikai.shared.json.JsonSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * {@code ./gradlew promptLint}: checks the runtime prompt standard (AGENTS.md §5) for every file
 * {@code prompts/{id}/v{n}.md}: front matter, variable declarations and placement, abstention
 * block, untrusted-data declaration, output schema, eval set, mirror document and the LOCK file.
 * Rule 3 (XML escaping of untrusted values) is a runtime property of {@code PromptRenderer}.
 */
public final class PromptLint {

  static final Set<String> STATUSES = Set.of("experimental", "active", "deprecated");
  static final Set<String> ROLES =
      Set.of("LOCAL_PII", "FAST", "REASONING", "VERIFIER", "VERIFIER_FALLBACK", "JUDGE");
  static final Set<String> EFFORTS = Set.of("low", "medium", "high");
  static final Map<String, String> ROLE_MODEL_KEYS =
      Map.of(
          "LOCAL_PII", "local-pii",
          "FAST", "fast",
          "REASONING", "reasoning",
          "VERIFIER", "verifier",
          "VERIFIER_FALLBACK", "verifier-fallback",
          "JUDGE", "judge");

  private static final Pattern FILE = Pattern.compile("v([1-9][0-9]*)\\.md");
  private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final Pattern VARIABLE =
      Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*}}");
  private static final Pattern CONTRACT_ELEMENT =
      Pattern.compile("<(contract_clause|contract_document)>(.*?)</\\1>", Pattern.DOTALL);
  private static final Pattern METRIC = Pattern.compile("M-[0-9]{2}");

  private final Path resources;
  private final Path docs;
  private final ObjectMapper mapper = new ObjectMapper();

  public PromptLint(Path resources, Path docs) {
    this.resources = resources;
    this.docs = docs;
  }

  public static void main(String[] args) throws IOException {
    List<String> violations = new PromptLint(Path.of(args[0]), Path.of(args[1])).run();
    if (!violations.isEmpty()) {
      violations.forEach(violation -> System.err.println("promptLint: " + violation));
      System.exit(1);
    }
    System.out.println("promptLint: OK");
  }

  public List<String> run() throws IOException {
    List<String> violations = new ArrayList<>();
    Path prompts = resources.resolve("prompts");
    Map<String, String> lock = readLock(prompts.resolve("LOCK"), violations);
    if (!Files.isDirectory(prompts)) {
      return violations;
    }
    Set<String> evalSets = evalSets();
    Set<String> claudeRoles = claudeRoles();
    List<Path> files;
    try (Stream<Path> walk = Files.walk(prompts)) {
      files =
          walk.filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".md"))
              .sorted()
              .toList();
    }
    for (Path file : files) {
      lintFile(prompts, file, evalSets, claudeRoles, lock, violations);
    }
    for (Map.Entry<String, String> entry : lock.entrySet()) {
      Path file = prompts.resolve(entry.getKey());
      if (!Files.isRegularFile(file)) {
        violations.add("LOCK lists " + entry.getKey() + " but the file does not exist");
      } else if (!sha256(Files.readAllBytes(file)).equals(entry.getValue())) {
        violations.add(entry.getKey() + ": released version changed (LOCK checksum mismatch)");
      }
    }
    return violations;
  }

  private void lintFile(
      Path prompts,
      Path file,
      Set<String> evalSets,
      Set<String> claudeRoles,
      Map<String, String> lock,
      List<String> violations)
      throws IOException {
    String where = prompts.relativize(file).toString();
    Path relative = prompts.relativize(file);
    Matcher name = FILE.matcher(file.getFileName().toString());
    if (relative.getNameCount() != 2
        || !name.matches()
        || !ID.matcher(relative.getName(0).toString()).matches()) {
      violations.add(where + ": prompt files must be prompts/{kebab-id}/v{n}.md");
      return;
    }
    String folder = relative.getName(0).toString();
    int version = Integer.parseInt(name.group(1));
    String text = Files.readString(file, StandardCharsets.UTF_8);
    if (!text.startsWith("---\n")) {
      violations.add(where + ": missing YAML front matter");
      return;
    }
    int end = text.indexOf("\n---\n", 4);
    if (end < 0) {
      violations.add(where + ": unterminated YAML front matter");
      return;
    }
    Map<String, Object> front;
    try {
      Object loaded =
          new Yaml(new SafeConstructor(new LoaderOptions())).load(text.substring(4, end));
      @SuppressWarnings("unchecked")
      Map<String, Object> map = loaded instanceof Map<?, ?> ? (Map<String, Object>) loaded : null;
      front = map;
    } catch (RuntimeException e) {
      violations.add(where + ": front matter is not valid YAML");
      return;
    }
    if (front == null) {
      violations.add(where + ": front matter must be a mapping");
      return;
    }
    String body = text.substring(end + 5);
    List<String> problems = new ArrayList<>();
    checkFrontMatter(front, folder, version, evalSets, claudeRoles, problems);
    Map<String, String> trust = variables(front, problems);
    checkBody(body, trust, problems);
    checkSchema(front, problems);
    if (!Files.isRegularFile(docs.resolve("prompts").resolve(folder + ".md"))) {
      problems.add("missing mirror document docs/prompts/" + folder + ".md");
    }
    Object status = front.get("status");
    if (("active".equals(status) || "deprecated".equals(status)) && !lock.containsKey(where)) {
      problems.add("status " + status + " requires an entry in prompts/LOCK");
    }
    problems.forEach(problem -> violations.add(where + ": " + problem));
  }

  private static void checkFrontMatter(
      Map<String, Object> front,
      String folder,
      int version,
      Set<String> evalSets,
      Set<String> claudeRoles,
      List<String> problems) {
    if (!folder.equals(front.get("id"))) {
      problems.add("id must equal the folder name " + folder);
    }
    if (!Integer.valueOf(version).equals(front.get("version"))) {
      problems.add("version must equal the file name v" + version);
    }
    if (!STATUSES.contains(String.valueOf(front.get("status")))) {
      problems.add("status must be one of " + STATUSES.stream().sorted().toList());
    }
    if (!(front.get("description") instanceof String description) || description.isBlank()) {
      problems.add("description is required");
    }
    String role = String.valueOf(front.get("model_role"));
    if (!ROLES.contains(role)) {
      problems.add("model_role must be one of " + ROLES.stream().sorted().toList());
    } else {
      boolean claude = claudeRoles.contains(role);
      Object temperature = front.get("temperature");
      if (claude && !"provider-default".equals(temperature)) {
        problems.add("temperature must be provider-default for the Claude role " + role);
      }
      if (!claude && !(temperature instanceof Number)) {
        problems.add("temperature must be a number for the non-Claude role " + role);
      }
      Object effort = front.get("effort");
      if (claude && !EFFORTS.contains(String.valueOf(effort))) {
        problems.add("effort must be low, medium or high for the Claude role " + role);
      }
      if (!claude && effort != null) {
        problems.add("effort is only allowed for Claude roles");
      }
    }
    positive(front, "max_output_tokens", problems);
    positive(front, "timeout_seconds", problems);
    if (front.get("examples") instanceof Integer examples && (examples < 0 || examples > 3)) {
      problems.add("examples must be between 0 and 3");
    }
    if (!(front.get("eval") instanceof Map<?, ?> eval)) {
      problems.add("eval with set and metrics is required");
    } else {
      if (!evalSets.contains(String.valueOf(eval.get("set")))) {
        problems.add("eval.set must name an eval set of docs/spec/evaluation.md §2");
      }
      if (!(eval.get("metrics") instanceof List<?> metrics)
          || metrics.isEmpty()
          || !metrics.stream().allMatch(m -> METRIC.matcher(String.valueOf(m)).matches())) {
        problems.add("eval.metrics must be a non-empty list of M-nn IDs");
      }
    }
    if (!(front.get("owner") instanceof String owner) || owner.isBlank()) {
      problems.add("owner is required");
    }
    if (!(front.get("changelog") instanceof List<?> changelog)
        || changelog.stream()
            .noneMatch(
                c ->
                    c instanceof Map<?, ?> m
                        && Integer.valueOf(version).equals(m.get("version")))) {
      problems.add("changelog needs an entry for version " + version);
    }
  }

  private static void positive(Map<String, Object> front, String key, List<String> problems) {
    if (!(front.get(key) instanceof Integer value) || value < 1) {
      problems.add(key + " must be a positive integer");
    }
  }

  /** Declared variables mapped to their trust level. */
  private static Map<String, String> variables(Map<String, Object> front, List<String> problems) {
    Map<String, String> trust = new TreeMap<>();
    if (!(front.get("input_variables") instanceof List<?> declared)) {
      problems.add("input_variables must be a list");
      return trust;
    }
    for (Object entry : declared) {
      if (!(entry instanceof Map<?, ?> variable)
          || !(variable.get("name") instanceof String variableName)
          || !Set.of("text", "json").contains(String.valueOf(variable.get("type")))
          || !Set.of("trusted", "untrusted").contains(String.valueOf(variable.get("trust")))
          || !(variable.get("description") instanceof String)) {
        problems.add("each input variable needs name, type (text|json), trust and description");
        continue;
      }
      trust.put(variableName, String.valueOf(variable.get("trust")));
    }
    return trust;
  }

  private static void checkBody(String body, Map<String, String> trust, List<String> problems) {
    Set<String> used = new LinkedHashSet<>();
    Matcher variable = VARIABLE.matcher(body);
    while (variable.find()) {
      used.add(variable.group(1));
      if (!trust.containsKey(variable.group(1))) {
        problems.add("{{" + variable.group(1) + "}} is not declared in input_variables");
      }
    }
    trust.keySet().stream()
        .filter(name -> !used.contains(name))
        .forEach(name -> problems.add("declared variable " + name + " is never used"));

    String system = element(body, "system");
    String user = element(body, "user");
    if (system == null) {
      problems.add("missing <system> element");
    } else {
      String abstention = element(system, "abstention");
      if (abstention == null || abstention.isBlank()) {
        problems.add("<system> needs a non-empty <abstention> element");
      }
      if (!system.contains("untrusted")
          || !(system.contains("contract_clause") || system.contains("contract_document"))) {
        problems.add("<system> must declare contract_clause/contract_document content untrusted");
      }
      if (VARIABLE.matcher(system).find()) {
        problems.add("<system> must not contain variables");
      }
    }
    if (user == null) {
      problems.add("missing <user> element");
    }
    String allowedZone = user == null ? "" : user;
    StringBuilder insideContract = new StringBuilder();
    Matcher contract = CONTRACT_ELEMENT.matcher(allowedZone);
    while (contract.find()) {
      insideContract.append(contract.group(2)).append('\n');
    }
    for (Map.Entry<String, String> entry : trust.entrySet()) {
      if (!"untrusted".equals(entry.getValue())) {
        continue;
      }
      Pattern placeholder =
          Pattern.compile("\\{\\{\\s*" + Pattern.quote(entry.getKey()) + "\\s*}}");
      long total = placeholder.matcher(body).results().count();
      long allowed = placeholder.matcher(insideContract).results().count();
      if (total != allowed) {
        problems.add(
            "untrusted variable "
                + entry.getKey()
                + " may appear only inside <contract_clause> or <contract_document> in <user>");
      }
    }
  }

  private void checkSchema(Map<String, Object> front, List<String> problems) {
    if (!(front.get("output_schema") instanceof String location) || location.isBlank()) {
      problems.add("output_schema is required");
      return;
    }
    Path schemaFile = resources.resolve(location);
    if (!Files.isRegularFile(schemaFile)) {
      problems.add("output_schema " + location + " does not exist");
      return;
    }
    JsonNode schema;
    try {
      schema = mapper.readTree(schemaFile.toFile());
    } catch (IOException e) {
      problems.add("output_schema " + location + " is not valid JSON");
      return;
    }
    if (!"https://json-schema.org/draft/2020-12/schema".equals(schema.path("$schema").asText())) {
      problems.add("output_schema must declare $schema 2020-12");
    }
    JsonSchemas.metaSchemaErrors(schema)
        .forEach(error -> problems.add("output_schema is not valid JSON Schema: " + error));
    closedObjects(schema, "$", problems);
  }

  /** Every object schema must set additionalProperties: false (AGENTS.md §5.3 rule 5). */
  static void closedObjects(JsonNode node, String path, List<String> problems) {
    if (node.isObject()) {
      boolean isObjectSchema =
          "object".equals(node.path("type").asText())
              || (node.path("type").isArray()
                  && node.path("type").toString().contains("\"object\""))
              || node.has("properties");
      if (isObjectSchema
          && !(node.has("additionalProperties")
              && node.get("additionalProperties").isBoolean()
              && !node.get("additionalProperties").asBoolean())) {
        problems.add("output_schema object at " + path + " must set additionalProperties: false");
      }
      node.properties()
          .forEach(
              entry -> {
                if (!entry.getKey().equals("enum")
                    && !entry.getKey().equals("const")
                    && !entry.getKey().equals("examples")
                    && !entry.getKey().equals("default")) {
                  closedObjects(entry.getValue(), path + "." + entry.getKey(), problems);
                }
              });
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        closedObjects(node.get(i), path + "[" + i + "]", problems);
      }
    }
  }

  private static String element(String text, String name) {
    Matcher matcher =
        Pattern.compile("<" + name + ">(.*?)</" + name + ">", Pattern.DOTALL).matcher(text);
    return matcher.find() ? matcher.group(1) : null;
  }

  private Set<String> evalSets() throws IOException {
    Path evaluation = docs.resolve("spec/evaluation.md");
    Set<String> sets = new LinkedHashSet<>();
    String text = Files.readString(evaluation);
    int start = text.indexOf("## 2. Eval sets");
    int end = text.indexOf("\n## ", start + 5);
    Matcher row = Pattern.compile("(?m)^\\| `([a-z-]+)` \\|").matcher(text.substring(start, end));
    while (row.find()) {
      sets.add(row.group(1));
    }
    return sets;
  }

  /** Roles whose configured model ID is a Claude model, read from application.yml. */
  private Set<String> claudeRoles() throws IOException {
    String yaml = Files.readString(resources.resolve("application.yml"));
    Object first =
        new Yaml(new SafeConstructor(new LoaderOptions())).loadAll(yaml).iterator().next();
    Map<?, ?> models = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) first).get("shromikai")).get("models");
    Set<String> claude = new LinkedHashSet<>();
    ROLE_MODEL_KEYS.forEach(
        (role, key) -> {
          if (String.valueOf(models.get(key)).startsWith("claude-")) {
            claude.add(role);
          }
        });
    return claude;
  }

  private static Map<String, String> readLock(Path lock, List<String> violations)
      throws IOException {
    Map<String, String> entries = new TreeMap<>();
    if (!Files.isRegularFile(lock)) {
      return entries;
    }
    for (String line : Files.readAllLines(lock)) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      String[] parts = trimmed.split("\\s+");
      if (parts.length != 3
          || !parts[1].matches("v[1-9][0-9]*")
          || !parts[2].matches("[0-9a-f]{64}")) {
        violations.add("LOCK line must be '{id} v{n} {sha256}': " + trimmed);
        continue;
      }
      entries.put(parts[0] + "/" + parts[1] + ".md", parts[2]);
    }
    return entries;
  }

  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
