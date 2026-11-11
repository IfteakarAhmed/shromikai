package bd.shromikai.privacy;

import java.util.List;

/** What the local model found in the text: exact strings, one list per kind (FR-29). */
public record LocalEntities(
    List<String> persons,
    List<String> organisations,
    List<String> addresses,
    List<String> employeeIds) {

  public static final LocalEntities NONE =
      new LocalEntities(List.of(), List.of(), List.of(), List.of());

  public LocalEntities {
    persons = List.copyOf(persons);
    organisations = List.copyOf(organisations);
    addresses = List.copyOf(addresses);
    employeeIds = List.copyOf(employeeIds);
  }

  public LocalEntities merge(LocalEntities other) {
    return new LocalEntities(
        concat(persons, other.persons),
        concat(organisations, other.organisations),
        concat(addresses, other.addresses),
        concat(employeeIds, other.employeeIds));
  }

  private static List<String> concat(List<String> a, List<String> b) {
    return java.util.stream.Stream.concat(a.stream(), b.stream()).distinct().toList();
  }

  @Override
  public String toString() {
    return "LocalEntities[masked]";
  }
}
