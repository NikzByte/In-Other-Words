package ai;

import java.util.Objects;
import java.util.Optional;

/**
 * The game's single entry point to the AI, with fallback built in.
 *
 * tryCheck() NEVER throws. If the tutor is turned off, has no key, is offline, times out,
 * or replies with something unreadable, it returns Optional.empty() and the game keeps
 * its normal exact-match checking and authored hint. The game never needs the AI to progress.
 */
public class TutorService {

    private final AiTutor tutor;
    private volatile boolean enabled = true;
    private volatile String lastProblem = "";

    public TutorService(AiTutor tutor) {
        this.tutor = Objects.requireNonNull(tutor, "Tutor must not be null.");
    }

    /** Gemini when GEMINI_API_KEY is set, otherwise the offline tutor. Never throws. */
    public static TutorService fromEnvironment() {
        try {
            GeminiTutor gemini = GeminiTutor.fromEnvironment();
            return new TutorService(gemini.isAvailable() ? gemini : new OfflineTutor());
        } catch (RuntimeException error) {
            return new TutorService(new OfflineTutor());
        }
    }

    /** The player's Settings choice. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** True when an AI exists on this computer (a key is set), whether or not the player turned it on. */
    public boolean isAvailable() {
        return tutor.isAvailable();
    }

    /** True when the game should ask the AI: turned on in Settings and available. */
    public boolean isReady() {
        return enabled && tutor.isAvailable();
    }

    /** Why the last check fell back, for debugging; empty when it worked. */
    public String getLastProblem() {
        return lastProblem;
    }

    /**
     * Asks the AI to judge an answer. Slow: call it from a background thread, never the Swing UI thread.
     *
     * @return the verdict, or empty when the game should fall back to normal checking
     */
    public Optional<AiVerdict> tryCheck(AnswerCheckRequest request) {
        if (request == null || !isReady()) {
            return Optional.empty();
        }
        try {
            AiVerdict verdict = tutor.check(request);
            lastProblem = "";
            return Optional.ofNullable(verdict);
        } catch (AiException error) {
            return fallBack(error.getMessage());
        } catch (RuntimeException error) {
            // Unexpected bugs must not crash the game either.
            return fallBack("Unexpected AI error: " + error);
        }
    }

    private Optional<AiVerdict> fallBack(String problem) {
        lastProblem = problem == null ? "Unknown AI problem." : problem;
        System.err.println("AI tutor unavailable, using normal checking: " + lastProblem);
        return Optional.empty();
    }
}
