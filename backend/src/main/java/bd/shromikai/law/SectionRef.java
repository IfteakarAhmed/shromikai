package bd.shromikai.law;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** One current section in an act's table of contents. */
@Schema(name = "SectionRef")
public record SectionRef(
    String sectionKey,
    String sectionNo,
    @Nullable String sectionNoOriginal,
    @Nullable String headingBn,
    @Nullable String headingEn) {}
