package ai;

/**
 * Thrown when the AI service cannot give a usable answer: no API key, no internet,
 * a timeout, an HTTP error, or a blocked/empty reply.
 * Callers catch this and fall back to the game's normal answer checking.
 */
public class AiException extends Exception {

    private static final long serialVersionUID = 1L;

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
