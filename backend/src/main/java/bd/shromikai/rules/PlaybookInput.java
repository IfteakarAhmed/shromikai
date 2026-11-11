package bd.shromikai.rules;

import java.util.List;

/** What a caller sends to create or replace a playbook (api.md §3 {@code PlaybookInput}). */
public record PlaybookInput(String name, String side, List<PlaybookRule> rules) {

  public PlaybookInput {
    rules = rules == null ? List.of() : List.copyOf(rules);
  }
}
