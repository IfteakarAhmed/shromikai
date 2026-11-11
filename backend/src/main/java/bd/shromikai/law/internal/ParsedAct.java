package bd.shromikai.law.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** An act as read from the portal: header facts, sections in source order and parse problems. */
public record ParsedAct(
    String title,
    String actNumberText,
    @Nullable LocalDate publishedOn,
    List<ParsedSection> sections,
    Map<String, String> amendingActs,
    List<String> problems) {}
