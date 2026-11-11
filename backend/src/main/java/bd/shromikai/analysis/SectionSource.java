package bd.shromikai.analysis;

import bd.shromikai.law.Section;
import java.util.Optional;

/** The statute corpus as the analysis uses it: the current section a key names. */
public interface SectionSource {

  /** The section with this key if it exists and is current. */
  Optional<Section> current(String sectionKey);
}
