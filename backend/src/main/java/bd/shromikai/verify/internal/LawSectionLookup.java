package bd.shromikai.verify.internal;

import bd.shromikai.law.LawReadService;
import bd.shromikai.law.LawReadService.SectionLookup;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@link SectionLookupPort} over the law read service. */
@Component
final class LawSectionLookup implements SectionLookupPort {

  private final LawReadService law;

  LawSectionLookup(LawReadService law) {
    this.law = law;
  }

  @Override
  public Optional<SectionLookup> find(String sectionKey) {
    return law.lookup(sectionKey);
  }
}
