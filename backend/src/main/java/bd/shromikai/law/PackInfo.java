package bd.shromikai.law;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A jurisdiction pack as the corpus covers it (architecture §11.4): the acts and ILO conventions
 * ingested and how many ILO mapping edges a human has reviewed (R-10). It carries no legal text.
 */
public record PackInfo(
    String code,
    String nameEn,
    @Nullable String nameBn,
    List<ActSummary> acts,
    List<ActSummary> iloConventions,
    long reviewedIloMappings) {

  public PackInfo {
    acts = List.copyOf(acts);
    iloConventions = List.copyOf(iloConventions);
  }
}
