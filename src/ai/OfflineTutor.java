package ai;

/**
 * The tutor used when there is no AI at all (for example, no API key on this computer).
 * It is never available, so the game always uses its normal exact-match checking.
 */
public class OfflineTutor implements AiTutor {

    @Override
    public AiVerdict check(AnswerCheckRequest request) throws AiException {
        throw new AiException("The AI tutor is offline.");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
