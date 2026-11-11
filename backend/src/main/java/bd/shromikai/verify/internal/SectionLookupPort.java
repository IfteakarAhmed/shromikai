package bd.shromikai.verify.internal;

import bd.shromikai.law.LawReadService.SectionLookup;
import java.util.Optional;

/** The verifier's view of the statute corpus (one exact section version by key). */
public interface SectionLookupPort {

  Optional<SectionLookup> find(String sectionKey);
}
