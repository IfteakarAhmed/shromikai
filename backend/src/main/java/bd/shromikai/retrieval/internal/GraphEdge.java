package bd.shromikai.retrieval.internal;

/** A graph edge from a seed section to a current target section. */
public record GraphEdge(long fromId, long toId, String kind) {}
