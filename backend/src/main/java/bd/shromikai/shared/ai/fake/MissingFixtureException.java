package bd.shromikai.shared.ai.fake;

/** No recorded response exists for the requested prompt, version and case. */
public final class MissingFixtureException extends IllegalStateException {

  public MissingFixtureException(String promptId, int promptVersion, String caseId) {
    super(
        "No fixture for "
            + promptId
            + " v"
            + promptVersion
            + " case "
            + caseId
            + ": run ./gradlew recordFixtures -Pcase="
            + caseId);
  }
}
