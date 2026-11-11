package bd.shromikai.shared.ai;

/**
 * A prompt's {@code dynamic_enums} entry (AGENTS.md §5.2): the schema node at {@code path}, for
 * example {@code $.findings[*].citations[*].sectionKey}, gets an {@code enum} filled per request
 * from {@code source}, which names the request variable the values come from.
 */
public record DynamicEnum(String path, String source) {}
