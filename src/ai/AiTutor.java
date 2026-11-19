package ai;

/**
 * Something that can judge a typed answer. The game depends on this interface,
 * not on Gemini directly, so another provider or an offline version can be swapped in.
 */
public interface AiTutor {

    /**
     * Judges one answer. Slow (may use the network): never call on the Swing UI thread.
     *
     * @throws AiException when no usable verdict is available; the caller must fall back
     */
    AiVerdict check(AnswerCheckRequest request) throws AiException;

    /** True when this tutor can be used at all, for example when an API key is set. */
    boolean isAvailable();
}
