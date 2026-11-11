package bd.shromikai.law.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The corpus data quality report (FR-17, R-07): sections per act, empty texts, missing source URLs
 * (fails the run), duplicate section numbers, sections missing a language, edge counts by type and
 * five sampled sections with links for manual comparison. Every figure is computed from the stored
 * corpus; percentages always carry their counts.
 */
record QualityReport(
    String status,
    @Nullable String corpusRelease,
    String generatedAt,
    String gitSha,
    long sampleSeed,
    List<ActLine> acts,
    List<IloLine> ilo,
    List<MissingLine> missing,
    Map<String, Long> edges,
    long unresolvedReferences,
    List<String> failures,
    List<Sample> samples) {

  /** What the builder needs about one act. {@code failure} is set when it could not be acquired. */
  record ActFacts(
      String code,
      String title,
      String jurisdiction,
      String authoritativeLang,
      String actStatus,
      String sourceKind,
      String parserVersion,
      String sourceUrl,
      boolean required,
      @Nullable String failure,
      int sourceDuplicateNumbers,
      List<String> amendingActs,
      List<String> problems) {}

  /** One stored section version. */
  record SectionFacts(
      String actCode,
      String sectionKey,
      String sectionNo,
      @Nullable String sourceUrl,
      boolean hasTextBn,
      boolean hasTextEn,
      boolean current,
      boolean omitted,
      String heading) {}

  record IloLine(
      String code,
      String titleLabel,
      String status,
      @Nullable String ratificationStatus,
      @Nullable String sourceUrl) {}

  record MissingLine(String what, String reason) {}

  record Input(
      List<ActFacts> acts,
      List<SectionFacts> sections,
      Map<String, Long> edgeCounts,
      long unresolvedReferences,
      List<IloLine> ilo,
      List<MissingLine> missing,
      @Nullable String corpusRelease,
      String gitSha,
      long sampleSeed,
      Instant generatedAt) {}

  record ActLine(
      String code,
      String title,
      String jurisdiction,
      String authoritativeLang,
      String actStatus,
      String sourceKind,
      String parserVersion,
      String sourceUrl,
      @Nullable String failure,
      int versions,
      int currentSections,
      int omittedSections,
      int emptyTexts,
      int missingSourceUrls,
      int duplicateNumbers,
      int missingAuthoritativeLang,
      int missingTranslation,
      List<String> amendingActs,
      List<String> problems) {}

  record Sample(String actCode, String sectionKey, String heading, String officialUrl) {}

  static final int SAMPLE_SIZE = 5;
  private static final int LISTED_KEYS = 20;

  static QualityReport build(Input in) {
    List<String> failures = new ArrayList<>();
    List<ActLine> lines = new ArrayList<>();
    for (ActFacts act : in.acts()) {
      lines.add(line(act, in.sections(), failures));
    }
    List<Sample> samples = sample(in);
    return new QualityReport(
        failures.isEmpty() ? "PASSED" : "FAILED",
        in.corpusRelease(),
        in.generatedAt().toString(),
        in.gitSha(),
        in.sampleSeed(),
        lines,
        in.ilo(),
        in.missing(),
        new TreeMap<>(in.edgeCounts()),
        in.unresolvedReferences(),
        failures,
        samples);
  }

  private static ActLine line(ActFacts act, List<SectionFacts> all, List<String> failures) {
    List<SectionFacts> rows = all.stream().filter(s -> s.actCode().equals(act.code())).toList();
    List<SectionFacts> current = rows.stream().filter(s -> s.current() && !s.omitted()).toList();
    int omitted = (int) rows.stream().filter(SectionFacts::omitted).count();
    boolean bn = act.authoritativeLang().equals("bn");
    int empty = (int) current.stream().filter(s -> !s.hasTextBn() && !s.hasTextEn()).count();
    int missingAuthoritative =
        (int) current.stream().filter(s -> !(bn ? s.hasTextBn() : s.hasTextEn())).count();
    int missingTranslation =
        (int) current.stream().filter(s -> !(bn ? s.hasTextEn() : s.hasTextBn())).count();
    List<String> noUrl =
        rows.stream()
            .filter(s -> s.sourceUrl() == null || s.sourceUrl().isBlank())
            .map(SectionFacts::sectionKey)
            .toList();
    Map<String, Long> byNumber = new LinkedHashMap<>();
    current.forEach(s -> byNumber.merge(s.sectionNo(), 1L, Long::sum));
    int duplicates =
        (int) byNumber.values().stream().filter(c -> c > 1).count() + act.sourceDuplicateNumbers();

    if (!noUrl.isEmpty()) {
      failures.add(
          "MISSING_SOURCE_URL: "
              + noUrl.size()
              + " section(s) of "
              + act.code()
              + " have no source URL, for example "
              + String.join(", ", noUrl.subList(0, Math.min(LISTED_KEYS, noUrl.size()))));
    }
    byNumber.forEach(
        (no, count) -> {
          if (count > 1) {
            failures.add(
                "DUPLICATE_CURRENT_SECTION: "
                    + act.code()
                    + " section "
                    + no
                    + " has "
                    + count
                    + " current versions");
          }
        });
    if (act.required() && act.failure() != null) {
      failures.add("ACT_FAILED: " + act.code() + ": " + act.failure());
    } else if (act.required() && current.isEmpty()) {
      failures.add("ACT_EMPTY: " + act.code() + " has no current section");
    }
    return new ActLine(
        act.code(),
        act.title(),
        act.jurisdiction(),
        act.authoritativeLang(),
        act.actStatus(),
        act.sourceKind(),
        act.parserVersion(),
        act.sourceUrl(),
        act.failure(),
        rows.size(),
        (int) rows.stream().filter(SectionFacts::current).count(),
        omitted,
        empty,
        noUrl.size(),
        duplicates,
        missingAuthoritative,
        missingTranslation,
        act.amendingActs(),
        act.problems());
  }

  private static List<Sample> sample(Input in) {
    List<SectionFacts> candidates =
        new ArrayList<>(
            in.sections().stream()
                .filter(s -> s.current() && !s.omitted() && (s.hasTextBn() || s.hasTextEn()))
                .filter(s -> s.sourceUrl() != null && !s.sourceUrl().isBlank())
                .sorted(Comparator.comparing(SectionFacts::sectionKey))
                .toList());
    Collections.shuffle(candidates, new Random(in.sampleSeed()));
    return candidates.stream()
        .limit(SAMPLE_SIZE)
        .map(s -> new Sample(s.actCode(), s.sectionKey(), s.heading(), s.sourceUrl()))
        .toList();
  }

  boolean failed() {
    return status.equals("FAILED");
  }

  void write(Path dir) {
    try {
      Files.createDirectories(dir);
      ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
      Files.writeString(
          dir.resolve("corpus-quality.json"),
          mapper.writeValueAsString(this) + "\n",
          StandardCharsets.UTF_8);
      Files.writeString(dir.resolve("corpus-quality.md"), markdown(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  String markdown() {
    StringBuilder md = new StringBuilder();
    md.append("# Corpus quality report\n\n");
    md.append("- Status: **").append(status).append("**\n");
    md.append("- Corpus release: ")
        .append(corpusRelease == null ? "none (run failed)" : corpusRelease)
        .append('\n');
    md.append("- Generated: ").append(generatedAt).append(" at git ").append(gitSha).append('\n');
    md.append(
        "- Generated by the ingestion code (`./gradlew ingestLaws`); every number below is computed from the stored corpus (R-07).\n\n");
    if (!failures.isEmpty()) {
      md.append("## Failures\n\n");
      failures.forEach(f -> md.append("- ").append(f).append('\n'));
      md.append('\n');
    }
    md.append("## Sections per act\n\n");
    md.append(
        "| Act | Source | Versions | Current | Omitted | Empty texts | Missing source URL | Duplicate numbers | Missing authoritative language | Missing translation |\n");
    md.append("| --- | --- | ---: | ---: | ---: | --- | --- | ---: | --- | --- |\n");
    for (ActLine a : acts) {
      md.append("| ")
          .append(a.code())
          .append(" (")
          .append(a.title())
          .append(") | ")
          .append(a.sourceKind())
          .append(" ")
          .append(a.parserVersion())
          .append(" | ")
          .append(a.versions())
          .append(" | ")
          .append(a.currentSections())
          .append(" | ")
          .append(a.omittedSections())
          .append(" | ")
          .append(share(a.emptyTexts(), nonOmittedCurrent(a)))
          .append(" | ")
          .append(share(a.missingSourceUrls(), a.versions()))
          .append(" | ")
          .append(a.duplicateNumbers())
          .append(" | ")
          .append(share(a.missingAuthoritativeLang(), nonOmittedCurrent(a)))
          .append(" | ")
          .append(share(a.missingTranslation(), nonOmittedCurrent(a)))
          .append(" |\n");
    }
    md.append(
        "\nCurrent sections exclude omitted ones; \"missing translation\" counts current sections without text in the act's non-authoritative language (the portal publishes one language per act).\n\n");
    md.append("## Acquisition notes\n\n");
    for (ActLine a : acts) {
      md.append("- **").append(a.code()).append("**: source ").append(a.sourceUrl());
      if (a.failure() != null) {
        md.append(" — FAILED: ").append(a.failure());
      }
      if (!a.amendingActs().isEmpty()) {
        md.append("; amending acts detected from footnotes: ")
            .append(a.amendingActs().size())
            .append(" (")
            .append(String.join(", ", a.amendingActs()))
            .append(')');
      }
      if (!a.problems().isEmpty()) {
        md.append("; parser problems: ").append(String.join("; ", a.problems()));
      }
      md.append('\n');
    }
    for (MissingLine m : missing) {
      md.append("- **").append(m.what()).append("** missing: ").append(m.reason()).append('\n');
    }
    md.append("\n## ILO conventions\n\n");
    if (ilo.isEmpty()) {
      md.append("None listed in the pack.\n");
    } else {
      md.append(
          "| Convention | Status | Bangladesh ratification (as published by NORMLEX) | Source |\n| --- | --- | --- | --- |\n");
      for (IloLine i : ilo) {
        md.append("| ")
            .append(i.code())
            .append(" (")
            .append(i.titleLabel())
            .append(") | ")
            .append(i.status())
            .append(" | ")
            .append(i.ratificationStatus() == null ? "—" : i.ratificationStatus())
            .append(" | ")
            .append(i.sourceUrl() == null ? "—" : i.sourceUrl())
            .append(" |\n");
      }
    }
    md.append("\n## Knowledge graph edges\n\n| Type | Count |\n| --- | ---: |\n");
    edges.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" |\n"));
    md.append(
            "\nReferences found in the text whose target section is not in the corpus (unresolved): ")
        .append(unresolvedReferences)
        .append(".\n\n");
    md.append("## Sections to compare with the official site (seed ")
        .append(sampleSeed)
        .append(")\n\n");
    for (Sample s : samples) {
      md.append("- `")
          .append(s.sectionKey())
          .append("` — ")
          .append(s.heading())
          .append(" — ")
          .append(s.officialUrl())
          .append('\n');
    }
    return md.toString();
  }

  private static int nonOmittedCurrent(ActLine a) {
    return a.currentSections();
  }

  private static String share(int part, int whole) {
    return part + " of " + whole;
  }
}
