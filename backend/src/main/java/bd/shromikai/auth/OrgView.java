package bd.shromikai.auth;

import java.util.UUID;

/** The organisation an account belongs to (api.md §3, {@code Me.org}). */
public record OrgView(UUID id, String name, String kind, String plan) {}
