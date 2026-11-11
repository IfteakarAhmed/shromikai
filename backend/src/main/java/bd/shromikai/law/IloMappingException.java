package bd.shromikai.law;

/**
 * The ILO mapping file is malformed or a reviewed row cannot be verified; startup stops (FR-16).
 */
public final class IloMappingException extends RuntimeException {

  public IloMappingException(String message) {
    super(message);
  }
}
