package bd.shromikai.rules;

/** A problem with one field of a request: a JSON pointer such as {@code /rules/0/unit}. */
public record FieldError(String pointer, String message) {}
