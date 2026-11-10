package bd.shromikai.auth;

/** User roles (api.md §6.1). Guests are {@code WORKER} with the {@code guest} claim. */
public enum Role {
  WORKER,
  HR,
  ADMIN
}
