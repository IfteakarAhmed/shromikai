package bd.shromikai.law.internal;

import java.util.List;

/**
 * A section as read from the source, in the act's authoritative language. {@code sectionNo} is the
 * Latin key form, {@code sectionNoOriginal} as printed. {@code text} is the display text (never
 * normalised); {@code omitted} sections carry the source's omission marker and are stored as
 * non-current (ADR-0011 decision 4).
 */
public record ParsedSection(
    int ordinal,
    String sectionNo,
    String sectionNoOriginal,
    String heading,
    String text,
    String chapterNo,
    String chapterName,
    String sourceUrl,
    boolean omitted,
    int mergedRows,
    List<Subsection> subsections,
    List<AmendmentNote> notes) {

  /** A numbered paragraph of the section text, for example {@code (২)}. */
  public record Subsection(String label, String text) {}

  /** Collects a section while the rows that continue it are read (ADR-0011 decision 3). */
  static final class Builder {
    private final int ordinal;
    private final String sectionNo;
    private final String sectionNoOriginal;
    private final String heading;
    private final String chapterNo;
    private final String chapterName;
    private final String sourceUrl;
    private final StringBuilder text;
    private final java.util.ArrayList<AmendmentNote> notes;
    private int rows = 1;

    Builder(
        int ordinal,
        String sectionNo,
        String sectionNoOriginal,
        String heading,
        String text,
        String chapterNo,
        String chapterName,
        String sourceUrl,
        List<AmendmentNote> notes) {
      this.ordinal = ordinal;
      this.sectionNo = sectionNo;
      this.sectionNoOriginal = sectionNoOriginal;
      this.heading = heading;
      this.chapterNo = chapterNo;
      this.chapterName = chapterName;
      this.sourceUrl = sourceUrl;
      this.text = new StringBuilder(text);
      this.notes = new java.util.ArrayList<>(notes);
    }

    void append(String continuationHeading, String continuationText, List<AmendmentNote> more) {
      rows++;
      text.append('\n').append(continuationHeading).append('\n').append(continuationText);
      notes.addAll(more);
    }

    /** A further line of the same section (PDF text has no row structure). */
    void appendLine(String line) {
      text.append('\n').append(line);
    }

    ParsedSection build() {
      String full = text.toString();
      boolean omitted =
          rows == 1
              && (AmendmentParser.isOmissionMarker(full)
                  || (AmendmentParser.isOmissionMarker(heading)
                      && (full.isBlank() || AmendmentParser.startsWithOmissionWord(full))));
      return new ParsedSection(
          ordinal,
          sectionNo,
          sectionNoOriginal,
          heading,
          full,
          chapterNo,
          chapterName,
          sourceUrl,
          omitted,
          rows,
          SubsectionSplitter.split(full),
          List.copyOf(notes));
    }
  }
}
