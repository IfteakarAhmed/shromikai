package bd.shromikai.analysis;

import bd.shromikai.law.LawReadService;
import bd.shromikai.law.Section;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@link SectionSource} over the law read service. */
@Component
final class LawSectionSource implements SectionSource {

  private final LawReadService law;

  LawSectionSource(LawReadService law) {
    this.law = law;
  }

  @Override
  public Optional<Section> current(String sectionKey) {
    return law.lookup(sectionKey).map(l -> l.section()).filter(Section::isCurrent);
  }
}
