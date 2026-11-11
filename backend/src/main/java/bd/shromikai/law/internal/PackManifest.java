package bd.shromikai.law.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** A jurisdiction pack manifest read from {@code data/packs/{code}.yaml} (architecture §11.4). */
record PackManifest(
    String code,
    String portalBaseUrl,
    List<ActDefinition> acts,
    List<IloConvention> iloConventions,
    String mappingFile) {

  private static final Pattern ACT_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9-]{1,31}");

  static PackManifest load(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
      return from(asMap(root, "pack"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value, String what) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(what + " must be a mapping");
    }
    return (Map<String, Object>) map;
  }

  private static String str(Map<String, Object> map, String key, boolean required) {
    Object value = map.get(key);
    if (value == null || value.toString().isBlank()) {
      if (required) {
        throw new IllegalArgumentException("missing '" + key + "'");
      }
      return null;
    }
    return value.toString().strip();
  }

  private static PackManifest from(Map<String, Object> root) {
    String code = str(root, "code", true);
    Map<String, Object> sources = asMap(root.getOrDefault("sources", Map.of()), "sources");
    Map<String, Object> bdlaws = asMap(sources.getOrDefault("bdlaws", Map.of()), "sources.bdlaws");
    String baseUrl = str(bdlaws, "base_url", false);

    List<ActDefinition> acts = new ArrayList<>();
    Set<String> codes = new HashSet<>();
    for (Object item : (List<?>) root.getOrDefault("acts", List.of())) {
      Map<String, Object> a = asMap(item, "act");
      String actCode = str(a, "code", true);
      if (!ACT_CODE.matcher(actCode).matches() || !codes.add(actCode)) {
        throw new IllegalArgumentException("invalid or duplicate act code: " + actCode);
      }
      String lang = str(a, "authoritative_lang", true);
      if (!lang.equals("bn") && !lang.equals("en")) {
        throw new IllegalArgumentException("authoritative_lang must be bn or en: " + actCode);
      }
      acts.add(
          new ActDefinition(
              actCode,
              str(a, "jurisdiction", true),
              str(a, "portal_act_id", false),
              str(a, "pdf_url", false),
              lang,
              str(a, "title_en", true),
              str(a, "title_bn", false),
              str(a, "definitions_section", false),
              !Boolean.FALSE.equals(a.get("required")),
              str(a, "note", false)));
    }

    List<IloConvention> ilo = new ArrayList<>();
    for (Object item : (List<?>) root.getOrDefault("ilo_conventions", List.of())) {
      Map<String, Object> c = asMap(item, "ilo convention");
      String convCode = str(c, "code", true);
      if (!convCode.matches("C[0-9]{3}") || !codes.add("ILO-" + convCode)) {
        throw new IllegalArgumentException("invalid or duplicate ILO convention: " + convCode);
      }
      String file = str(c, "text_file", false);
      String ratification = str(c, "ratification_file", false);
      ilo.add(
          new IloConvention(
              convCode,
              str(c, "title_label", true),
              str(c, "normlex_url", true),
              str(c, "proposed_by", true),
              str(c, "reviewed_by", false),
              file == null ? "data/raw/ilo/" + convCode + ".text.html" : file,
              ratification == null
                  ? "data/raw/ilo/" + convCode + ".ratification.html"
                  : ratification));
    }
    return new PackManifest(code, baseUrl, acts, ilo, str(root, "ilo_mapping_file", false));
  }
}
